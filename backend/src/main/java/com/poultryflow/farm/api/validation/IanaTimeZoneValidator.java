package com.poultryflow.farm.api.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.time.ZoneId;

final class IanaTimeZoneValidator implements ConstraintValidator<IanaTimeZone, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || ZoneId.getAvailableZoneIds().contains(value.trim());
    }
}
