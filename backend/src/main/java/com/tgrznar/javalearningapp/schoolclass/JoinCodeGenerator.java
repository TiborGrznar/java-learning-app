package com.tgrznar.javalearningapp.schoolclass;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Generates join codes that students use to enter a class.
 * Codes come from {@link SecureRandom} so they cannot be predicted from earlier ones.
 * The alphabet has 32 characters (A-Z without I and O, digits 2-9): characters that look alike are left out
 * so a teacher can read a code aloud or write it on a board. 32 is a power of two, so every character is
 * equally likely.
 */
@Component
@RequiredArgsConstructor
public class JoinCodeGenerator {

    static final int CODE_LENGTH = 8;
    static final int MAX_ATTEMPTS = 10;
    static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final SchoolClassRepository classRepository;
    private final SecureRandom random = new SecureRandom();

    /**
     * Returns a code that no class uses yet. The check and the later insert are not atomic, so two
     * concurrent requests could draw the same code; the UNIQUE constraint on the column then rejects the second.
     */
    public String generateUnique() {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String code = generate();
            if (!classRepository.existsByJoinCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException(
                "Could not generate a unique join code in " + MAX_ATTEMPTS + " attempts");
    }

    String generate() {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }
}