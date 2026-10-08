package com.tgrznar.javalearningapp.user.admin;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TemporaryPasswordGeneratorTest {

    private final TemporaryPasswordGenerator generator = new TemporaryPasswordGenerator();

    @Test
    void generate_hasTheConfiguredLength() {
        assertThat(generator.generate()).hasSize(TemporaryPasswordGenerator.LENGTH);
    }

    @Test
    void generate_usesOnlyAllowedCharacters() {
        for (int i = 0; i < 200; i++) {
            for (char c : generator.generate().toCharArray()) {
                assertThat(TemporaryPasswordGenerator.ALPHABET).contains(String.valueOf(c));
            }
        }
    }

    @Test
    void generate_returnsDifferentPasswords() {
        Set<String> passwords = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            passwords.add(generator.generate());
        }
        assertThat(passwords).hasSize(50);
    }
}