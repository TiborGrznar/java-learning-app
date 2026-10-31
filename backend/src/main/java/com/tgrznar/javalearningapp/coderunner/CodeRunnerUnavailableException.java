package com.tgrznar.javalearningapp.coderunner;

/**
 * The runner could not be reached, timed out, refused the request (a configuration or backend fault,
 * never the student's fault) or answered something that cannot be trusted. The code was not judged.
 * The message never contains the token or any source code.
 */
public class CodeRunnerUnavailableException extends RuntimeException {

    public CodeRunnerUnavailableException(String message) {
        super(message);
    }

    public CodeRunnerUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
