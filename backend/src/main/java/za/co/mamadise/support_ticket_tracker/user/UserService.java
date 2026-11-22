package za.co.mamadise.support_ticket_tracker.user;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import za.co.mamadise.support_ticket_tracker.assignment.AssignmentService;
import za.co.mamadise.support_ticket_tracker.ticket.PageResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates accounts and changes roles. This is the only place a password is hashed. There is deliberately no public
 * registration endpoint; accounts come from the optional first-start bootstrap (and tests), and roles only change
 * through the administrator-only endpoint.
 */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    private static final java.util.Set<Role> STAFF = java.util.EnumSet.of(Role.ADMIN, Role.SUPPORT_AGENT);

    /** No '@' allowed, so a login identifier containing '@' is always an email. */
    private static final Pattern USERNAME = Pattern.compile("^[a-z0-9][a-z0-9._-]{2,49}$");
    private static final Pattern EMAIL = Pattern.compile("^[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}$");

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final AssignmentService assignments;

    /** {@code assignments} is lazy only to keep bean creation order simple; it has no dependency on this class. */
    public UserService(AppUserRepository users, PasswordEncoder passwordEncoder, PasswordPolicy passwordPolicy,
            @Lazy AssignmentService assignments) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.assignments = assignments;
    }

    /** Trims and lower-cases (locale independent). Null becomes the empty string. */
    public static String normalise(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    /** Creates an ordinary {@link Role#USER} account. */
    @Transactional
    public UserResponse createUser(String username, String email, String rawPassword) {
        return createUser(username, email, rawPassword, Role.USER);
    }

    /**
     * Creates an account with an explicit role. Internal use only (the opt-in bootstrap and tests): no HTTP endpoint
     * may pass a client-supplied role here, and registration never will.
     */
    @Transactional
    public UserResponse createUser(String username, String email, String rawPassword, Role role) {
        Objects.requireNonNull(role, "role");
        String normalisedUsername = normalise(username);
        String normalisedEmail = normalise(email);

        Map<String, String> errors = new LinkedHashMap<>();
        if (!USERNAME.matcher(normalisedUsername).matches()) {
            errors.put("username", "Username must be 3-50 characters: letters, digits, '.', '_' or '-'");
        }
        if (normalisedEmail.length() > 254 || !EMAIL.matcher(normalisedEmail).matches()) {
            errors.put("email", "Email address is not valid");
        }
        List<String> passwordProblems = passwordPolicy.violations(rawPassword, normalisedUsername);
        if (!passwordProblems.isEmpty()) {
            errors.put("password", String.join("; ", passwordProblems));
        }
        if (!errors.isEmpty()) {
            throw new InvalidUserException(errors);
        }

        Map<String, String> taken = new LinkedHashMap<>();
        if (users.existsByUsername(normalisedUsername)) {
            taken.put("username", "Username is already in use");
        }
        if (users.existsByEmail(normalisedEmail)) {
            taken.put("email", "Email is already in use");
        }
        if (!taken.isEmpty()) {
            throw new DuplicateUserException(taken);
        }

        try {
            AppUser saved = users.saveAndFlush(
                    new AppUser(normalisedUsername, normalisedEmail, passwordEncoder.encode(rawPassword), role));
            return UserResponse.from(saved);
        } catch (DataIntegrityViolationException race) {
            // Lost a race with a concurrent create: the unique keys are the final guard.
            throw new DuplicateUserException(Map.of("username", "Username or email is already in use"));
        }
    }

    /**
     * Administrator view, paged: optional case-insensitive search in username, email and full name, and optional
     * role / enabled filters. Ordered by username.
     */
    @Transactional(readOnly = true)
    public PageResponse<UserResponse> searchUsers(String q, Role role, Boolean enabled, int page, int size) {
        List<Specification<AppUser>> parts = new java.util.ArrayList<>();
        if (q != null && !q.isBlank()) {
            String pattern = "%" + q.trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%")
                    .replace("_", "\\_") + "%";
            parts.add((r, cq, cb) -> cb.or(cb.like(r.get("username"), pattern, '\\'),
                    cb.like(r.get("email"), pattern, '\\'), cb.like(cb.lower(r.get("fullName")), pattern, '\\')));
        }
        if (role != null) {
            parts.add((r, cq, cb) -> cb.equal(r.get("role"), role));
        }
        if (enabled != null) {
            parts.add((r, cq, cb) -> cb.equal(r.get("enabled"), enabled));
        }
        PageRequest request = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100),
                Sort.by("username").and(Sort.by("id")));
        return PageResponse.from(users.findAll(Specification.allOf(parts), request), UserResponse::from);
    }

    /** Administrator creates an account; role defaults to USER. */
    @Transactional
    public UserResponse createByAdmin(AdminUserCreateRequest request) {
        UserResponse created = createUser(request.username(), request.email(), request.password(),
                request.role() == null ? Role.USER : request.role());
        String fullName = cleanFullName(request.fullName());
        if (fullName != null) {
            AppUser user = users.findById(created.id()).orElseThrow();
            user.setFullName(fullName);
            created = UserResponse.from(users.saveAndFlush(user));
        }
        log.info("Account {} created by an administrator with role {}", created.id(), created.role());
        return created;
    }

    /**
     * Administrator edits email, full name, role and activation. An administrator cannot change their own role or
     * deactivate themselves (so at least one active administrator always remains). Deactivating an account, or taking
     * away its staff role, removes it from its active ticket assignments; its sessions end on the next request.
     */
    @Transactional
    public UserResponse updateByAdmin(Long actorId, Long targetId, AdminUserUpdateRequest request) {
        AppUser target = users.findById(targetId).orElseThrow(() -> new UserNotFoundException(targetId));
        boolean self = Objects.equals(actorId, targetId);
        if (self && request.role() != target.getRole()) {
            throw new RoleChangeNotAllowedException("You cannot change your own role");
        }
        if (self && !request.enabled()) {
            throw new RoleChangeNotAllowedException("You cannot deactivate your own account");
        }
        String email = validateEmail(request.email(), targetId);
        boolean losesStaffAccess = target.isStaff() && (!request.enabled() || !STAFF.contains(request.role()));
        target.setEmail(email);
        target.setFullName(cleanFullName(request.fullName()));
        target.setRole(request.role());
        target.setEnabled(request.enabled());
        AppUser saved = users.saveAndFlush(target);
        if (losesStaffAccess) {
            assignments.unassignAllFrom(targetId, actorId);
        }
        log.info("Account {} updated by administrator {} (role {}, enabled {})", targetId, actorId, saved.getRole(),
                saved.isEnabled());
        return UserResponse.from(saved);
    }

    @Transactional
    public void resetPassword(Long targetId, String newPassword) {
        AppUser target = users.findById(targetId).orElseThrow(() -> new UserNotFoundException(targetId));
        setPassword(target, newPassword, "newPassword");
        log.info("Password of account {} reset by an administrator", targetId);
    }

    @Transactional(readOnly = true)
    public UserResponse profile(Long userId) {
        return UserResponse.from(users.findById(userId).orElseThrow(() -> new UserNotFoundException(userId)));
    }

    @Transactional
    public UserResponse updateProfile(Long userId, ProfileUpdateRequest request) {
        AppUser user = users.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
        user.setEmail(validateEmail(request.email(), userId));
        user.setFullName(cleanFullName(request.fullName()));
        return UserResponse.from(users.saveAndFlush(user));
    }

    /** Wrong current password is a 400 on the field (not a 401), so the client's session handling is not triggered. */
    @Transactional
    public void changeOwnPassword(Long userId, PasswordChangeRequest request) {
        AppUser user = users.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new InvalidUserException(Map.of("currentPassword", "Current password is not correct"));
        }
        if (request.currentPassword().equals(request.newPassword())) {
            throw new InvalidUserException(Map.of("newPassword", "The new password must be different"));
        }
        setPassword(user, request.newPassword(), "newPassword");
        log.info("Account {} changed its password", userId);
    }

    /** Active staff that can be assigned tickets, optionally only members of one team. */
    @Transactional(readOnly = true)
    public List<UserSummary> assignableStaff(Long teamId) {
        List<AppUser> found = teamId == null ? users.findEnabledByRoleIn(STAFF)
                : users.findEnabledTeamMembersByRoleIn(teamId, STAFF);
        return found.stream().map(UserSummary::of).toList();
    }

    private void setPassword(AppUser user, String rawPassword, String field) {
        List<String> problems = passwordPolicy.violations(rawPassword, user.getUsername());
        if (!problems.isEmpty()) {
            throw new InvalidUserException(Map.of(field, String.join("; ", problems)));
        }
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        users.saveAndFlush(user);
    }

    private String validateEmail(String email, Long ownerId) {
        String normalised = normalise(email);
        if (normalised.length() > 254 || !EMAIL.matcher(normalised).matches()) {
            throw new InvalidUserException(Map.of("email", "Email address is not valid"));
        }
        if (users.existsByEmailAndIdNot(normalised, ownerId)) {
            throw new DuplicateUserException(Map.of("email", "Email is already in use"));
        }
        return normalised;
    }

    private static String cleanFullName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return null;
        }
        String trimmed = fullName.trim().replaceAll("\\s+", " ");
        if (trimmed.length() > 100 || trimmed.chars().anyMatch(Character::isISOControl)) {
            throw new InvalidUserException(Map.of("fullName", "Full name must be at most 100 characters of text"));
        }
        return trimmed;
    }

    /** Administrator view: every account, oldest first. */
    @Transactional(readOnly = true)
    public List<UserResponse> listUsers() {
        return users.findAll(org.springframework.data.domain.Sort.by("id")).stream().map(UserResponse::from).toList();
    }

    /**
     * Changes another account's role. An administrator cannot change their own role, which also guarantees that the
     * last administrator can never remove themselves. Existing sessions of the target pick the change up on their next
     * request (see SessionRoleRefreshFilter).
     */
    @Transactional
    public UserResponse changeRole(Long actorId, Long targetId, Role newRole) {
        Objects.requireNonNull(newRole, "role");
        if (Objects.equals(actorId, targetId)) {
            throw new RoleChangeNotAllowedException("You cannot change your own role");
        }
        AppUser target = users.findById(targetId).orElseThrow(() -> new UserNotFoundException(targetId));
        boolean losesStaffAccess = target.isStaff() && !STAFF.contains(newRole);
        target.setRole(newRole);
        AppUser saved = users.saveAndFlush(target);
        if (losesStaffAccess) {
            assignments.unassignAllFrom(targetId, actorId);
        }
        log.info("Role of account {} changed to {} by administrator {}", targetId, newRole, actorId);
        return UserResponse.from(saved);
    }
}
