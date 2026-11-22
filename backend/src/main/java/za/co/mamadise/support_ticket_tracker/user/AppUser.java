package za.co.mamadise.support_ticket_tracker.user;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Persistent account. Internal to the backend: the API only ever exposes {@link UserResponse}.
 * The table is owned by Flyway (V4, V5); any column change here needs a new migration.
 * Username and email are kept normalised (trimmed, lower case) so uniqueness is case-insensitive.
 */
@Entity
@Table(name = "app_users")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, unique = true, length = 254)
    private String email;

    /** BCrypt hash. Never the password itself, never serialised to a client. */
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false)
    private boolean enabled = true;

    /** Optional display name ("Thandi Mokoena"), max 100 characters. */
    @Column(name = "full_name", length = 100)
    private String fullName;

    /** Exactly one role per account. Defaults to the least privileged role; never ADMIN unless set explicitly. */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private Role role = Role.USER;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(nullable = false)
    private Instant updatedAt;

    protected AppUser() {
        // for JPA
    }

    public AppUser(String username, String email, String passwordHash) {
        this(username, email, passwordHash, Role.USER);
    }

    public AppUser(String username, String email, String passwordHash, Role role) {
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        setRole(role);
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    /** Must already be normalised and validated (see UserService). */
    public void setEmail(String email) {
        this.email = email;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    /** The full name when set, otherwise the username. */
    public String displayName() {
        return fullName == null || fullName.isBlank() ? username : fullName;
    }

    /** Must already be a BCrypt hash produced by UserService. */
    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public boolean isStaff() {
        return role == Role.ADMIN || role == Role.SUPPORT_AGENT;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = java.util.Objects.requireNonNull(role, "role");
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /** Deliberately leaves the hash out. */
    @Override
    public String toString() {
        return "AppUser[id=" + id + ", username=" + username + ", role=" + role + ", enabled=" + enabled + "]";
    }
}
