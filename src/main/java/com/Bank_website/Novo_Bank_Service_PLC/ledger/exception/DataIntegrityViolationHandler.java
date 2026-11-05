package com.Bank_website.Novo_Bank_Service_PLC.ledger.exception;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * Last-resort mapping for database constraint violations that TransferService could not resolve
 * (for example a CHECK violation). Duplicate idempotency keys are resolved in the service itself.
 */
@ControllerAdvice(basePackages = "com.Bank_website.Novo_Bank_Service_PLC.ledger.controller")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DataIntegrityViolationHandler {

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<GlobalExceptionHandler.ErrorResponse> handle(DataIntegrityViolationException ex) {
        return GlobalExceptionHandler.build(HttpStatus.CONFLICT, "Request conflicts with existing data");
    }
}
