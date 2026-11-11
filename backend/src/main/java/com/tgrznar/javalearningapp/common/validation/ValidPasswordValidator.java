package com.tgrznar.javalearningapp.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.nio.charset.StandardCharsets;

/** Checks {@link ValidPassword}: the minimum is counted in characters, the maximum in UTF-8 bytes. */
public class ValidPasswordValidator implements ConstraintValidator<ValidPassword, String> {

    static final int MIN_LENGTH = 8;

    // BCrypt ignores everything beyond 72 bytes (bytes, not characters).
    public static final int MAX_BYTES = 72;

    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        if (password == null) {
            return true;
        }
        // A code point is one character for the user, even when it is stored as two UTF-16 units (an emoji).
        int characters = password.codePointCount(0, password.length());
        return characters >= MIN_LENGTH && password.getBytes(StandardCharsets.UTF_8).length <= MAX_BYTES;
    }
}
