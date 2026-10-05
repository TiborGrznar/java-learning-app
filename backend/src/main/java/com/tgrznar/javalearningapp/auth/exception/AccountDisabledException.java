package com.tgrznar.javalearningapp.auth.exception;

/** Thrown when a correctly authenticated user has a deactivated account (is_active = false). */
public class AccountDisabledException extends RuntimeException {

    public AccountDisabledException() {
        super("Účet je deaktivovaný");
    }
}