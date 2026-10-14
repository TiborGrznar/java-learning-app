package com.tgrznar.javalearningapp.common;

import com.tgrznar.javalearningapp.auth.exception.AccountDisabledException;
import com.tgrznar.javalearningapp.auth.exception.EmailAlreadyExistsException;
import com.tgrznar.javalearningapp.auth.exception.InvalidAccessTokenException;
import com.tgrznar.javalearningapp.auth.exception.InvalidCredentialsException;
import com.tgrznar.javalearningapp.auth.exception.InvalidRefreshTokenException;
import com.tgrznar.javalearningapp.auth.exception.PasswordMismatchException;
import com.tgrznar.javalearningapp.learning.exception.ModuleLockedException;
import com.tgrznar.javalearningapp.learning.quiz.exception.InvalidQuizTokenException;
import com.tgrznar.javalearningapp.learning.quiz.exception.InvalidQuizAnswersException;
import com.tgrznar.javalearningapp.learning.quiz.exception.QuizNotAvailableException;
import com.tgrznar.javalearningapp.learning.quiz.exception.QuizOutdatedException;
import com.tgrznar.javalearningapp.learning.quiz.exception.TheoryNotCompletedException;
import com.tgrznar.javalearningapp.module.exception.ModuleNotFoundException;
import com.tgrznar.javalearningapp.quizquestion.exception.QuestionNotFoundException;
import com.tgrznar.javalearningapp.school.exception.SchoolNameAlreadyExistsException;
import com.tgrznar.javalearningapp.school.exception.SchoolNotFoundException;
import com.tgrznar.javalearningapp.school.exception.SchoolInactiveException;
import com.tgrznar.javalearningapp.schoolclass.exception.*;

import com.tgrznar.javalearningapp.user.exception.InvalidUserDataException;
import com.tgrznar.javalearningapp.user.exception.LastAdminException;
import com.tgrznar.javalearningapp.user.exception.SelfModificationException;
import com.tgrznar.javalearningapp.user.exception.TeacherLeadsClassesException;
import com.tgrznar.javalearningapp.user.exception.UserNotFoundException;
import com.tgrznar.javalearningapp.user.exception.UserNotDeletableException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
 * code                           status  meaning / expected frontend reaction
 * VALIDATION_ERROR               400     invalid form input, show fieldErrors next to the fields
 * INVALID_REQUEST_BODY           400     unreadable JSON, a client bug, not shown to the user
 * PASSWORD_MISMATCH              400     password and confirmation differ, show in the form
 * INVALID_QUIZ_TOKEN             400     quiz token is not valid for this student and module, reload the quiz
 * INVALID_CREDENTIALS            401     wrong e-mail or password (indistinguishable on purpose)
 * INVALID_REFRESH_TOKEN          401     session expired, drop tokens and redirect to login
 * INVALID_TOKEN                  401     access token refers to a missing user, drop tokens, redirect to login
 * UNAUTHENTICATED                401     access token missing, invalid or expired, try /refresh once, then redirect to login
 * ACCOUNT_DISABLED               403     account deactivated, show a message, no retry
 * ACCESS_DENIED                  403     logged in but lacking the role, show a message, keep the tokens
 * MODULE_LOCKED                  403     module is locked until the previous module's quiz is passed, show it as locked, no retry
 * NOT_ENROLLED                   403     the student has not joined a class yet, send them to the join code form (POST /api/v1/enrollments)
 * SCHOOL_NOT_FOUND               404     school with the given id does not exist, show a message
 * USER_NOT_FOUND                 404     user with the given id does not exist, show a message
 * MODULE_NOT_FOUND               404     module with the given id does not exist, show a message
 * QUESTION_NOT_FOUND             404     question does not exist in the given module, show a message
 * CLASS_NOT_FOUND                404     class with the given id does not exist, show a message
 * CLASS_NAME_ALREADY_EXISTS      409     class name is taken in this school, show in the class form
 * EMAIL_ALREADY_EXISTS           409     e-mail is taken, show in the registration or user form
 * SCHOOL_NAME_ALREADY_EXISTS     409     school name is taken, show in the school form
 * SCHOOL_INACTIVE                409     school is deactivated, ask the admin to choose another one
 * LAST_ADMIN                     409     the last active administrator cannot be deactivated or demoted, show a message
 * SELF_MODIFICATION_NOT_ALLOWED  409     admin cannot change own role or deactivate self, show a message
 * USER_NOT_DELETABLE             409     only a student can be deleted, the user is a teacher or an administrator, offer deactivation instead
 * TEACHER_LEADS_CLASSES          409     the teacher leads an active class and cannot be deactivated, demoted or moved, reassign or deactivate the class first
 * INVALID_JOIN_CODE              404     no active class with an active school has this join code (unknown and inactive are not told apart)
 * ALREADY_ENROLLED               409     the student already belongs to a class
 * SCHOOL_MISMATCH                409     the student's school differs from the school of the class
 * TOO_MANY_ATTEMPTS              429     too many failed enrollment attempts, the Retry-After header says how long to wait
 * THEORY_NOT_COMPLETED           409     the module's theory was not marked as read yet, send the student to the theory
 * QUIZ_NOT_AVAILABLE             409     the module has no quiz questions, hide the quiz button
 * QUIZ_OUTDATED                  409     the quiz expired or one of its questions was deleted, reload it and start again
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

    /** 404 SCHOOL_NOT_FOUND: no school with the requested id. */
    @ExceptionHandler(SchoolNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleSchoolNotFound() {
        return ErrorResponse.of("SCHOOL_NOT_FOUND", "Škola neexistuje");
    }

    /** 409 SCHOOL_NAME_ALREADY_EXISTS: another school already uses this name (case-insensitive). */
    @ExceptionHandler(SchoolNameAlreadyExistsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleSchoolNameExists() {
        return ErrorResponse.of("SCHOOL_NAME_ALREADY_EXISTS", "Škola s týmto názvom už existuje");
    }

    /** 400 VALIDATION_ERROR: a business rule on one form field (e.g. teacher without a school). */
    @ExceptionHandler(InvalidUserDataException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleInvalidUserData(InvalidUserDataException ex) {
        return new ErrorResponse("VALIDATION_ERROR", "Formulár obsahuje chyby",
                Map.of(ex.getField(), ex.getMessage()));
    }

    /** 409 SCHOOL_INACTIVE: the chosen school is deactivated and cannot get new users. */
    @ExceptionHandler(SchoolInactiveException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleSchoolInactive() {
        return ErrorResponse.of("SCHOOL_INACTIVE", "Škola je neaktívna");
    }

    /** 404 USER_NOT_FOUND: no user with the requested id. */
    @ExceptionHandler(UserNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleUserNotFound() {
        return ErrorResponse.of("USER_NOT_FOUND", "Používateľ neexistuje");
    }

    /** 404 MODULE_NOT_FOUND: no module with the requested id. */
    @ExceptionHandler(ModuleNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleModuleNotFound() {
        return ErrorResponse.of("MODULE_NOT_FOUND", "Modul neexistuje");
    }

    /** 404 QUESTION_NOT_FOUND: no question with the requested id in the requested module. */
    @ExceptionHandler(QuestionNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleQuestionNotFound() {
        return ErrorResponse.of("QUESTION_NOT_FOUND", "Otázka neexistuje");
    }

    /** 403 MODULE_LOCKED: the student has not passed the quiz of the previous module yet. */
    @ExceptionHandler(ModuleLockedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ErrorResponse handleModuleLocked() {
        return ErrorResponse.of("MODULE_LOCKED", "Modul je zamknutý. Najprv úspešne dokončite kvíz v predchádzajúcom module");
    }

    /** 409 THEORY_NOT_COMPLETED: the quiz needs the module's theory to be marked as read first. */
    @ExceptionHandler(TheoryNotCompletedException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleTheoryNotCompleted() {
        return ErrorResponse.of("THEORY_NOT_COMPLETED", "Najprv si prečítajte teóriu modulu");
    }

    /** 409 QUIZ_NOT_AVAILABLE: the module has no quiz questions. */
    @ExceptionHandler(QuizNotAvailableException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleQuizNotAvailable() {
        return ErrorResponse.of("QUIZ_NOT_AVAILABLE", "Kvíz nie je dostupný");
    }

    /** 409 QUIZ_OUTDATED: the quiz token expired or one of its questions no longer exists. */
    @ExceptionHandler(QuizOutdatedException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleQuizOutdated() {
        return ErrorResponse.of("QUIZ_OUTDATED", "Kvíz sa zmenil alebo vypršal, načítajte ho znova");
    }

    /** 400 INVALID_QUIZ_TOKEN: the quiz token is malformed, tampered with, or issued for someone else. */
    @ExceptionHandler(InvalidQuizTokenException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleInvalidQuizToken() {
        return ErrorResponse.of("INVALID_QUIZ_TOKEN", "Neplatný kvíz, načítajte ho znova");
    }

    /** 400 VALIDATION_ERROR: the submitted answers do not match the questions in the quiz token. */
    @ExceptionHandler(InvalidQuizAnswersException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleInvalidQuizAnswers() {
        return new ErrorResponse("VALIDATION_ERROR", "Formulár obsahuje chyby",
                Map.of("answers", "Odpovede nezodpovedajú otázkam kvízu"));
    }

    /** 409 LAST_ADMIN: the change would leave the system without an active administrator. */
    @ExceptionHandler(LastAdminException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleLastAdmin() {
        return ErrorResponse.of("LAST_ADMIN", "Nemožno odobrať posledného aktívneho administrátora");
    }

    /** 409 SELF_MODIFICATION_NOT_ALLOWED: an administrator tried to change own role or deactivate self. */
    @ExceptionHandler(SelfModificationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleSelfModification() {
        return ErrorResponse.of("SELF_MODIFICATION_NOT_ALLOWED",
                "Nemôžete zmeniť vlastnú rolu ani deaktivovať vlastný účet");
    }

    /** 409 USER_NOT_DELETABLE: only students are deleted, teachers and administrators are deactivated. */
    @ExceptionHandler(UserNotDeletableException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleUserNotDeletable() {
        return ErrorResponse.of("USER_NOT_DELETABLE",
                "Zmazať možno len študenta. Učiteľa alebo administrátora deaktivujte");
    }

    /** 404 CLASS_NOT_FOUND: no class with the requested id. */
    @ExceptionHandler(SchoolClassNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleClassNotFound() {
        return ErrorResponse.of("CLASS_NOT_FOUND", "Trieda neexistuje");
    }

    /** 409 CLASS_NAME_ALREADY_EXISTS: the school already has a class with this name (case-insensitive). */
    @ExceptionHandler(ClassNameAlreadyExistsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleClassNameExists() {
        return ErrorResponse.of("CLASS_NAME_ALREADY_EXISTS", "Trieda s týmto názvom už v škole existuje");
    }

    /** 400 VALIDATION_ERROR: a business rule on one field of the class form (e.g. the teacher's school differs). */
    @ExceptionHandler(InvalidClassDataException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleInvalidClassData(InvalidClassDataException ex) {
        return new ErrorResponse("VALIDATION_ERROR", "Formulár obsahuje chyby",
                Map.of(ex.getField(), ex.getMessage()));
    }

    /** 409 TEACHER_LEADS_CLASSES: the teacher leads an active class and cannot be deactivated, demoted or moved. */
    @ExceptionHandler(TeacherLeadsClassesException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleTeacherLeadsClasses() {
        return ErrorResponse.of("TEACHER_LEADS_CLASSES",
                "Učiteľ vedie aktívnu triedu. Najprv triedu preraďte inému učiteľovi alebo ju deaktivujte");
    }

    /** 404 INVALID_JOIN_CODE: an unknown code and an inactive class give the same answer on purpose. */
    @ExceptionHandler(InvalidJoinCodeException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleInvalidJoinCode() {
        return ErrorResponse.of("INVALID_JOIN_CODE", "Kód triedy je neplatný");
    }

    /** 409 ALREADY_ENROLLED: the student already belongs to a class. */
    @ExceptionHandler(AlreadyEnrolledException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleAlreadyEnrolled() {
        return ErrorResponse.of("ALREADY_ENROLLED", "Už ste zaradený do triedy");
    }

    /** 409 SCHOOL_MISMATCH: the school set by an administrator differs from the school of the class. */
    @ExceptionHandler(SchoolMismatchException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleSchoolMismatch() {
        return ErrorResponse.of("SCHOOL_MISMATCH",
                "Váš účet patrí do inej školy, ako je trieda. Kontaktujte administrátora");
    }

    /** 429 TOO_MANY_ATTEMPTS: the Retry-After header tells the client how many seconds to wait. */
    @ExceptionHandler(TooManyAttemptsException.class)
    public ResponseEntity<ErrorResponse> handleTooManyAttempts(TooManyAttemptsException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(e.getRetryAfterSeconds()))
                .body(ErrorResponse.of("TOO_MANY_ATTEMPTS",
                        "Príliš veľa neúspešných pokusov o pripojenie do triedy. Skúste to znova neskôr"));
    }

    /** 403 NOT_ENROLLED: a student without a class opened the learning content, see EnrollmentGateInterceptor. */
    @ExceptionHandler(NotEnrolledException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ErrorResponse handleNotEnrolled() {
        return ErrorResponse.of("NOT_ENROLLED", "Najprv sa pripojte k triede pomocou kódu od učiteľa");
    }
}