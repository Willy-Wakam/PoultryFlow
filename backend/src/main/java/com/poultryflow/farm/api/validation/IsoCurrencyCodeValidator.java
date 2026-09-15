package com.poultryflow.farm.api.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Currency;
import java.util.Locale;

final class IsoCurrencyCodeValidator implements ConstraintValidator<IsoCurrencyCode, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        try {
            Currency.getInstance(value.trim().toUpperCase(Locale.ROOT));
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
