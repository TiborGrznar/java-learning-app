package com.tgrznar.javalearningapp.user.admin;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/** Generates one-time passwords for accounts created by an administrator. */
@Component
public class TemporaryPasswordGenerator {

    static final int LENGTH = 16;

    // No 0/O/1/l/I: the administrator reads the password out or copies it by hand.
    static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        StringBuilder password = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            password.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return password.toString();
    }
}