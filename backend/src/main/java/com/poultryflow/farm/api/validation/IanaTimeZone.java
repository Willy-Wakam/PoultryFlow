package com.poultryflow.farm.api.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;

@Documented
@Constraint(validatedBy = IanaTimeZoneValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface IanaTimeZone {

    String message() default "must be a valid IANA timezone";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
