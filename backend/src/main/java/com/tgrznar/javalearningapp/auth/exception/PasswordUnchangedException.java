package com.tgrznar.javalearningapp.auth.exception;

/** Thrown when the new password of a password change is the same as the current one. */
public class PasswordUnchangedException extends RuntimeException {

    public PasswordUnchangedException() {
        super("Nové heslo musí byť iné ako aktuálne");
    }
}
