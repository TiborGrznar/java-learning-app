package com.tgrznar.javalearningapp.common.validation;

import com.tgrznar.javalearningapp.auth.dto.RegisterRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ValidPasswordValidatorTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    private final ValidPasswordValidator validator = new ValidPasswordValidator();

    @Test
    void null_isValid_becauseAMissingPasswordIsNotBlankSJob() {
        assertThat(valid(null)).isTrue();
    }

    @Test
    void sevenCharacters_isTooShort() {
        assertThat(valid("a".repeat(7))).isFalse();
    }

    @Test
    void eightCharacters_isValid() {
        assertThat(valid("a".repeat(8))).isTrue();
    }

    @Test
    void exactly72AsciiCharacters_isValid() {
        assertThat(valid("a".repeat(72))).isTrue();
    }

    @Test
    void asciiOver72Bytes_isInvalid() {
        assertThat(valid("a".repeat(73))).isFalse();
    }

    @Test
    void shortPasswordWithDiacritics_isValid() {
        // 8 characters, 16 bytes
        assertThat(valid("á".repeat(8))).isTrue();
    }

    @Test
    void diacriticsFillingExactly72Bytes_isValid() {
        // 36 characters of 2 bytes each
        assertThat(valid("á".repeat(36))).isTrue();
    }

    @Test
    void diacriticsOver72Bytes_isInvalidAlthoughItIsShortInCharacters() {
        // 37 characters, 74 bytes: a limit counted in characters would accept it
        assertThat(valid("á".repeat(37))).isFalse();
    }

    @Test
    void emojiFilling72Bytes_isValid() {
        // 18 characters of 4 bytes each
        assertThat(valid("😀".repeat(18))).isTrue();
    }

    @Test
    void emojiOver72Bytes_isInvalid() {
        assertThat(valid("😀".repeat(19))).isFalse();
    }

    @Test
    void registerRequest_rejectsPasswordOverTheByteLimitOnThePasswordField() {
        String password = "á".repeat(37);
        RegisterRequest request = new RegisterRequest("Jano", "Novak", "jano@example.com", password, password);

        Set<ConstraintViolation<RegisterRequest>> violations = VALIDATOR.validate(request);

        assertThat(violations).hasSize(1);
        ConstraintViolation<RegisterRequest> violation = violations.iterator().next();
        assertThat(violation.getPropertyPath().toString()).isEqualTo("password");
        assertThat(violation.getMessage()).contains("72 bajtov");
    }

    @Test
    void registerRequest_acceptsPasswordOfExactly72Bytes() {
        String password = "á".repeat(36);
        RegisterRequest request = new RegisterRequest("Jano", "Novak", "jano@example.com", password, password);

        assertThat(VALIDATOR.validate(request)).isEmpty();
    }

    private boolean valid(String password) {
        return validator.isValid(password, null);
    }
}
