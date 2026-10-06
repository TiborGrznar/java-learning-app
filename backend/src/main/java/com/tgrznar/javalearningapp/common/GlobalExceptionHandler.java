package com.tgrznar.javalearningapp.common;

import com.tgrznar.javalearningapp.auth.exception.AccountDisabledException;
import com.tgrznar.javalearningapp.auth.exception.EmailAlreadyExistsException;
import com.tgrznar.javalearningapp.auth.exception.InvalidAccessTokenException;
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

/**
 * Translates exceptions into a uniform JSON error body with a stable error code.
 * <p>
 * Frontend contract: the client decides by {@code code}, never by the message text.
 * <pre>
 * code                   status  meaning / expected frontend reaction
 * VALIDATION_ERROR       400     invalid form input, show fieldErrors next to the fields
 * INVALID_REQUEST_BODY   400     unreadable JSON, a client bug, not shown to the user
 * PASSWORD_MISMATCH      400     password and confirmation differ, show in the form
 * INVALID_CREDENTIALS    401     wrong e-mail or password (indistinguishable on purpose)
 * INVALID_REFRESH_TOKEN  401     session expired, drop tokens and redirect to login
 * INVALID_TOKEN          401     access token refers to a missing user, drop tokens, redirect to login
 * UNAUTHENTICATED        401     access token missing, invalid or expired, try /refresh once, then redirect to login
 * ACCOUNT_DISABLED       403     account deactivated, show a message, no retry
 * ACCESS_DENIED          403     logged in but lacking the role, show a message, keep the tokens
 * EMAIL_ALREADY_EXISTS   409     e-mail is taken, show in the registration form
 * </pre>
 * <p>
 * UNAUTHENTICATED and ACCESS_DENIED are produced by the security filter chain, not by this class:
 * see JsonAuthenticationEntryPoint and JsonAccessDeniedHandler in auth.security.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 400 VALIDATION_ERROR: Bean Validation failed; fieldErrors maps field name to message. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        return new ErrorResponse("VALIDATION_ERROR", "Formulár obsahuje chyby", fieldErrors);
    }

    /** 400 INVALID_REQUEST_BODY: missing or malformed JSON body. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleUnreadableBody() {
        return ErrorResponse.of("INVALID_REQUEST_BODY", "Neplatné telo požiadavky");
    }

    /** 400 PASSWORD_MISMATCH: password and confirmPassword differ (registration). */
    @ExceptionHandler(PasswordMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handlePasswordMismatch(PasswordMismatchException ex) {
        return ErrorResponse.of("PASSWORD_MISMATCH", ex.getMessage());
    }

    /** 409 EMAIL_ALREADY_EXISTS: registration with an e-mail that already has an account. */
    @ExceptionHandler(EmailAlreadyExistsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleEmailExists(EmailAlreadyExistsException ex) {
        return ErrorResponse.of("EMAIL_ALREADY_EXISTS", ex.getMessage());
    }

    /** 401 INVALID_CREDENTIALS: login with an unknown e-mail or a wrong password (same answer for both). */
    @ExceptionHandler(InvalidCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorResponse handleInvalidCredentials(InvalidCredentialsException ex) {
        return ErrorResponse.of("INVALID_CREDENTIALS", ex.getMessage());
    }

    /** 401 INVALID_REFRESH_TOKEN: refresh token is unknown, expired or already used. */
    @ExceptionHandler(InvalidRefreshTokenException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorResponse handleInvalidRefreshToken(InvalidRefreshTokenException ex) {
        return ErrorResponse.of("INVALID_REFRESH_TOKEN", ex.getMessage());
    }

    /** 401 INVALID_TOKEN: a valid access token whose user no longer exists in the database. */
    @ExceptionHandler(InvalidAccessTokenException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorResponse handleInvalidAccessToken(InvalidAccessTokenException ex) {
        return ErrorResponse.of("INVALID_TOKEN", ex.getMessage());
    }

    /** 403 ACCOUNT_DISABLED: the account is deactivated (reported after a correct password / valid token). */
    @ExceptionHandler(AccountDisabledException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ErrorResponse handleAccountDisabled(AccountDisabledException ex) {
        return ErrorResponse.of("ACCOUNT_DISABLED", ex.getMessage());
    }
}