package com.tgrznar.javalearningapp.user.exception;

/** The teacher leads an active class, so the account cannot be deactivated, demoted or moved to another school. */
public class TeacherLeadsClassesException extends RuntimeException {

    public TeacherLeadsClassesException() {
        super("Teacher leads an active class");
    }
}