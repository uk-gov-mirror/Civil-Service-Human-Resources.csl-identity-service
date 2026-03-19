package uk.gov.cabinetoffice.csl.validation.annotation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import uk.gov.cabinetoffice.csl.validation.validator.UidMapRequestValidator;

import java.lang.annotation.*;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = UidMapRequestValidator.class)
@Documented
public @interface UidMapRequestUidsAndEmails {
    String message() default "Either uids or emails must be provided";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
