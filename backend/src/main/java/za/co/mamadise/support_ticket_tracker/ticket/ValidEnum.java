package za.co.mamadise.support_ticket_tracker.ticket;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.RECORD_COMPONENT;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * The annotated string must be the exact name of a constant of the given enum (null is valid;
 * combine with {@code @NotNull} when a value is required). Validating the name here, rather
 * than letting JSON binding fail, gives clients a per-field error message.
 */
@Documented
@Constraint(validatedBy = EnumNameValidator.class)
@Target({ FIELD, PARAMETER, RECORD_COMPONENT })
@Retention(RUNTIME)
public @interface ValidEnum {

    Class<? extends Enum<?>> value();

    String message() default "Invalid value";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
