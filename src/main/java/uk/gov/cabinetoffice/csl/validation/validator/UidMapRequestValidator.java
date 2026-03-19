package uk.gov.cabinetoffice.csl.validation.validator;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import uk.gov.cabinetoffice.csl.dto.UidMapRequest;
import uk.gov.cabinetoffice.csl.validation.annotation.UidMapRequestUidsAndEmails;

public class UidMapRequestValidator implements ConstraintValidator<UidMapRequestUidsAndEmails, UidMapRequest> {

    @Override
    public boolean isValid(UidMapRequest value, ConstraintValidatorContext context) {
        boolean uidsPresent = value.getUids() != null && !value.getUids().isEmpty();
        boolean emailsPresent = value.getEmails() != null && !value.getEmails().isEmpty();

        return uidsPresent || emailsPresent;
    }
}
