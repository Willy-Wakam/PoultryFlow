package com.poultryflow.farm.api.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Locale;
import java.util.Set;

final class IsoCountryCodeValidator implements ConstraintValidator<IsoCountryCode, String> {

    private static final Set<String> COUNTRY_CODES = Set.of(Locale.getISOCountries());

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null
                || COUNTRY_CODES.contains(value.trim().toUpperCase(Locale.ROOT));
    }
}
