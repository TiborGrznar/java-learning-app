package com.tgrznar.javalearningapp.learning.exception;

/** Thrown when a student asks for a module before passing the quiz of the previous one. */
public class ModuleLockedException extends RuntimeException {

    public ModuleLockedException(Long id) {
        super("Module is locked: " + id);
    }
}