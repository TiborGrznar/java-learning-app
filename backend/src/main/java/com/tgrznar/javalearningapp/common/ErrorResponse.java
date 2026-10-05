package com.tgrznar.javalearningapp.common;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * Uniform error body. The code is machine-readable (frontend logic), the message is Slovak
 * text meant for the user. fieldErrors is present only for validation errors.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(String code, String message, Map<String, String> fieldErrors) {

    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(code, message, null);
    }
}