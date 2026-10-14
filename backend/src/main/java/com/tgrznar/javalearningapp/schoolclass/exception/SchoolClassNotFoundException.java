package com.tgrznar.javalearningapp.schoolclass.exception;

/** No school class with the requested id exists. Named so it does not clash with java.lang.ClassNotFoundException. */
public class SchoolClassNotFoundException extends RuntimeException {

    public SchoolClassNotFoundException(Long id) {
        super("School class not found: " + id);
    }
}