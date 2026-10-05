package com.tgrznar.javalearningapp.common;

import com.tgrznar.javalearningapp.auth.exception.AccountDisabledException;
import com.tgrznar.javalearningapp.auth.exception.EmailAlreadyExistsException;
import com.tgrznar.javalearningapp.auth.exception.InvalidCredentialsException;
import com.tgrznar.javalearningapp.auth.exception.InvalidRefreshTokenException;
import com.tgrznar.javalearningapp.auth.exception.PasswordMismatchException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/** Translates exceptions into a uniform JSON error body with a stable error code. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        return new ErrorResponse("VALIDATION_ERROR", "Formulár obsahuje chyby", fieldErrors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleUnreadableBody() {
        return ErrorResponse.of("INVALID_REQUEST_BODY", "Neplatné telo požiadavky");
    }

    @ExceptionHandler(PasswordMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handlePasswordMismatch(PasswordMismatchException ex) {
        return ErrorResponse.of("PASSWORD_MISMATCH", ex.getMessage());
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleEmailExists(EmailAlreadyExistsException ex) {
        return ErrorResponse.of("EMAIL_ALREADY_EXISTS", ex.getMessage());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorResponse handleInvalidCredentials(InvalidCredentialsException ex) {
        return ErrorResponse.of("INVALID_CREDENTIALS", ex.getMessage());
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorResponse handleInvalidRefreshToken(InvalidRefreshTokenException ex) {
        return ErrorResponse.of("INVALID_REFRESH_TOKEN", ex.getMessage());
    }

    @ExceptionHandler(AccountDisabledException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ErrorResponse handleAccountDisabled(AccountDisabledException ex) {
        return ErrorResponse.of("ACCOUNT_DISABLED", ex.getMessage());
    }
}