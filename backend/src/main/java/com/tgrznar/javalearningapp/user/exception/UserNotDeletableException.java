package com.tgrznar.javalearningapp.user.exception;

/** Only students are deleted. Teachers and administrators are deactivated instead. */
public class UserNotDeletableException extends RuntimeException {

    public UserNotDeletableException(Long id) {
        super("User " + id + " is not a student and cannot be deleted");
    }
}