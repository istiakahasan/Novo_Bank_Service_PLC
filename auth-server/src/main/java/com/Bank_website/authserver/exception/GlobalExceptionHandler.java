package com.Bank_website.authserver.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.OffsetDateTime;
import java.util.stream.Collectors;

/** Every error is {status, message, timestamp}. Messages never echo request bodies (they can hold passwords). */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    public record ErrorResponse(int status, String message, OffsetDateTime timestamp) {
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> validation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage()) // field name + rule text, never the rejected value
                .collect(Collectors.joining(", "));
        return build(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            IllegalArgumentException.class})
    public ResponseEntity<ErrorResponse> badRequest(Exception ex) {
        return build(HttpStatus.BAD_REQUEST, "Malformed request");
    }

    @ExceptionHandler({InvalidCredentialsException.class, UnauthorizedException.class, AuthenticationException.class})
    public ResponseEntity<ErrorResponse> unauthorized(RuntimeException ex) {
        String message = ex instanceof UnauthorizedException ? ex.getMessage() : "Invalid credentials";
        return build(HttpStatus.UNAUTHORIZED, message);
    }

    @ExceptionHandler({ForbiddenException.class, AccessDeniedException.class})
    public ResponseEntity<ErrorResponse> forbidden(RuntimeException ex) {
        return build(HttpStatus.FORBIDDEN, ex instanceof ForbiddenException ? ex.getMessage() : "Access denied");
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> notFound(UserNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<ErrorResponse> conflict(DuplicateEmailException ex) {
        return build(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(AccountLockedException.class)
    public ResponseEntity<ErrorResponse> locked(AccountLockedException ex) {
        return withRetryAfter(HttpStatus.LOCKED, ex.getMessage(), ex.getRetryAfterSeconds());
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ErrorResponse> rateLimited(RateLimitExceededException ex) {
        return withRetryAfter(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage(), ex.getRetryAfterSeconds());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> unexpected(Exception ex) {
        log.error("Unhandled error: {}", ex.getClass().getSimpleName(), ex); // class + stack only, no request data
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(status.value(), message, OffsetDateTime.now()));
    }

    private ResponseEntity<ErrorResponse> withRetryAfter(HttpStatus status, String message, long seconds) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(seconds))
                .body(new ErrorResponse(status.value(), message, OffsetDateTime.now()));
    }
}
