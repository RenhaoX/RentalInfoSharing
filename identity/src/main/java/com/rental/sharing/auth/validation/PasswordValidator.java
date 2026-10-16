package com.rental.sharing.auth.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.nio.charset.StandardCharsets;

public class PasswordValidator implements ConstraintValidator<ValidPassword, String> {
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return false;
        }
        int length = value.codePointCount(0, value.length());
        return length >= 8 && length <= 64 && value.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}
