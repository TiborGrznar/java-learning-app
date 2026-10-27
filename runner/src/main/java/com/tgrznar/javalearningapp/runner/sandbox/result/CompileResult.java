package com.tgrznar.javalearningapp.runner.sandbox.result;

/**
 * Result of the compilation.
 *
 * @param success  true when javac finished with exit code 0 within the time limit
 * @param timedOut true when the compilation exceeded its time limit
 * @param message  sanitized compiler output, empty on success
 */
public record CompileResult(boolean success, boolean timedOut, String message) {
}