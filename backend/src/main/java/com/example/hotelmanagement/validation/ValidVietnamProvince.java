package com.example.hotelmanagement.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = VietnamProvinceValidator.class)
@Documented
public @interface ValidVietnamProvince {
    String message() default "Invalid province. Must be a valid province name from Vietnam.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
