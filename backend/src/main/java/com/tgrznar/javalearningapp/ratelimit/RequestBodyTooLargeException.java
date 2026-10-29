package com.tgrznar.javalearningapp.ratelimit;

import java.io.IOException;

/** The request body is larger than allowed. Thrown while the body is read, see {@link BodySizeLimitFilter}. */
public class RequestBodyTooLargeException extends IOException {

    public RequestBodyTooLargeException() {
        super("Request body is too large");
    }
}