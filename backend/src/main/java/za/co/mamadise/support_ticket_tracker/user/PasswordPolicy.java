package za.co.mamadise.support_ticket_tracker.user;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

/**
 * Password rules applied when an account is created (never at login, so existing passwords keep working if the
 * rules tighten). Length matters most; BCrypt only reads the first 72 bytes, so longer passwords are refused
 * instead of being silently truncated.
 */
@Component
public class PasswordPolicy {

    public static final int MIN_LENGTH = 10;
    public static final int MAX_BYTES = 72;

    /** @return one message per broken rule; empty when the password is acceptable */
    public List<String> violations(String password, String username) {
        List<String> problems = new ArrayList<>();
        if (password == null || password.isBlank()) {
            problems.add("Password is required");
            return problems;
        }
        if (password.codePointCount(0, password.length()) < MIN_LENGTH) {
            problems.add("Password must be at least " + MIN_LENGTH + " characters");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            problems.add("Password must be at most " + MAX_BYTES + " bytes (about " + MAX_BYTES + " characters)");
        }
        if (password.chars().noneMatch(Character::isLetter) || password.chars().noneMatch(Character::isDigit)) {
            problems.add("Password must contain at least one letter and one digit");
        }
        if (username != null && password.equalsIgnoreCase(username)) {
            problems.add("Password must not be the same as the username");
        }
        return problems;
    }
}
