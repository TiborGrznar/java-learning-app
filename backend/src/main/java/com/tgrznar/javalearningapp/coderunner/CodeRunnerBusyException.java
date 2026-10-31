package com.tgrznar.javalearningapp.coderunner;

/** The runner has no free slot and no room in its queue. The caller may try again after the given time. */
public class CodeRunnerBusyException extends RuntimeException {

    private final long retryAfterSeconds;

    public CodeRunnerBusyException(long retryAfterSeconds) {
        super("The code runner is busy");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
