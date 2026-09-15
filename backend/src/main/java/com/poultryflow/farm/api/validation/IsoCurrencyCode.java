package com.poultryflow.farm.api.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;

@Documented
@Constraint(validatedBy = IsoCurrencyCodeValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface IsoCurrencyCode {

    String message() default "must be a valid ISO-4217 currency code";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
