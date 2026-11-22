package za.co.mamadise.support_ticket_tracker.user;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PasswordPolicyTest {

    private final PasswordPolicy policy = new PasswordPolicy();

    @Test
    void acceptsALongPasswordWithLettersAndDigits() {
        assertThat(policy.violations("correct horse 42", "thandi")).isEmpty();
    }

    @Test
    void rejectsNullBlankShortAndSingleClassPasswords() {
        assertThat(policy.violations(null, "u")).containsExactly("Password is required");
        assertThat(policy.violations("   ", "u")).containsExactly("Password is required");
        assertThat(policy.violations("abc123", "u")).anyMatch(m -> m.contains("at least 10"));
        assertThat(policy.violations("onlyletterspassword", "u")).anyMatch(m -> m.contains("letter and one digit"));
        assertThat(policy.violations("12345678901234", "u")).anyMatch(m -> m.contains("letter and one digit"));
    }

    @Test
    void rejectsPasswordsBcryptWouldTruncate() {
        assertThat(policy.violations("a1".repeat(37), "u")).anyMatch(m -> m.contains("at most 72"));
        assertThat(policy.violations("a1".repeat(36), "u")).isEmpty();
    }

    @Test
    void rejectsPasswordEqualToUsername() {
        assertThat(policy.violations("Thandi12345", "thandi12345")).anyMatch(m -> m.contains("same as the username"));
    }
}
