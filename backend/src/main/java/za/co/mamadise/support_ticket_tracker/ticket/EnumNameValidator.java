package za.co.mamadise.support_ticket_tracker.ticket;

import java.util.HashSet;
import java.util.Set;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Implements {@link ValidEnum}. */
public class EnumNameValidator implements ConstraintValidator<ValidEnum, String> {

    private final Set<String> names = new HashSet<>();

    @Override
    public void initialize(ValidEnum annotation) {
        names.clear();
        for (Enum<?> constant : annotation.value().getEnumConstants()) {
            names.add(constant.name());
        }
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || names.contains(value);
    }
}
