package com.tgrznar.javalearningapp.runner.sandbox;

/** All sandbox slots and the queue are taken; the caller should answer 503 and the client retry later. */
public class SandboxBusyException extends RuntimeException {

    public SandboxBusyException() {
        super("Sandbox is busy");
    }
}