package com.tgrznar.javalearningapp.user.exception;

/** The operation would leave the system without an active administrator. */
public class LastAdminException extends RuntimeException {

    public LastAdminException() {
        super("The last active administrator cannot be removed");
    }
}