package com.tgrznar.javalearningapp.ratelimit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Builds limiter keys that must not hold personal data in readable form (for example e-mails). */
public final class LimiterKeys {

    private LimiterKeys() {
    }

    /** SHA-256 of the value as 64 hex characters: stable, fixed length, not readable. */
    public static String hashed(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }
}