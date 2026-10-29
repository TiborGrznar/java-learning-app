package com.tgrznar.javalearningapp.runner.security;

import java.io.IOException;

/** Thrown while a request body is read and has grown past the limit. */
public class RequestBodyTooLargeException extends IOException {

    public RequestBodyTooLargeException(long limit) {
        super("Request body exceeds " + limit + " bytes");
    }
}
