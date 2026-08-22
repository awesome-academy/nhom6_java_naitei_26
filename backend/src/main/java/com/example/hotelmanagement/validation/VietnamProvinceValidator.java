package com.example.hotelmanagement.validation;

import com.example.hotelmanagement.services.VietnamProvinceService;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class VietnamProvinceValidator implements ConstraintValidator<ValidVietnamProvince, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }
        return VietnamProvinceService.isValidProvince(value);
    }
}
