package com.Bank_website.auth.controller;

import com.Bank_website.auth.service.AuthException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.util.stream.Collectors;

@RestControllerAdvice
public class AuthExceptionHandler {

    public record ErrorResponse(int status, String message, OffsetDateTime timestamp) {
    }

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ErrorResponse> auth(AuthException ex) {
        return build(ex.status(), ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> invalid(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage()).collect(Collectors.joining(", "));
        return build(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(status.value(), message, OffsetDateTime.now()));
    }
}
