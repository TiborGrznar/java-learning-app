package com.tgrznar.javalearningapp.auth.exception;

/** Thrown when login, refresh or logout comes from a browser page whose Origin is not trusted. */
public class OriginNotAllowedException extends RuntimeException {

    public OriginNotAllowedException() {
        super("Požiadavka z tohto pôvodu nie je povolená");
    }
}
