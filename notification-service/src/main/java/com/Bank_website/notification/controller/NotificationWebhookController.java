package com.Bank_website.notification.controller;

import com.Bank_website.notification.dto.CustomerCreatedEvent;
import com.Bank_website.notification.dto.IgnoredResult;
import com.Bank_website.notification.dto.TransferCompletedEvent;
import com.Bank_website.notification.security.QStashSignatureVerifier;
import com.Bank_website.notification.service.NotificationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

/**
 * QStash fans EVERY event on the topic out to EVERY endpoint, so each webhook must ignore
 * events that are not its own (answering 200 so QStash does not retry).
 */
@RestController
@RequestMapping("/webhooks")
public class NotificationWebhookController {

    private static final Logger log = LoggerFactory.getLogger(NotificationWebhookController.class);
    private static final String SIGNATURE_HEADER = "Upstash-Signature";

    private final NotificationService notificationService;
    private final QStashSignatureVerifier verifier;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    public NotificationWebhookController(NotificationService notificationService, QStashSignatureVerifier verifier,
                                         ObjectMapper objectMapper, Validator validator) {
        this.notificationService = notificationService;
        this.verifier = verifier;
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    @PostMapping("/transfer-completed")
    public ResponseEntity<?> transferCompleted(
            @RequestHeader(value = SIGNATURE_HEADER, required = false) String signature,
            @RequestBody String body) throws JsonProcessingException {
        if (!verifier.verify(signature, body)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        TransferCompletedEvent event = objectMapper.readValue(body, TransferCompletedEvent.class);
        if (!"transfer.completed".equals(event.event())) {
            log.info("IGNORED event={} at /webhooks/transfer-completed", event.event());
            return ResponseEntity.ok(new IgnoredResult(true, "not a transfer.completed event"));
        }
        validate(event);
        return ResponseEntity.ok(notificationService.notifyTransferCompleted(event));
    }

    @PostMapping("/customer-created")
    public ResponseEntity<?> customerCreated(
            @RequestHeader(value = SIGNATURE_HEADER, required = false) String signature,
            @RequestBody String body) throws JsonProcessingException {
        if (!verifier.verify(signature, body)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        CustomerCreatedEvent event = objectMapper.readValue(body, CustomerCreatedEvent.class);
        if (!"customer.created".equals(event.event())) {
            log.info("IGNORED event={} at /webhooks/customer-created", event.event());
            return ResponseEntity.ok(new IgnoredResult(true, "not a customer.created event"));
        }
        validate(event);
        return ResponseEntity.ok(notificationService.notifyCustomerCreated(event));
    }

    private <T> void validate(T event) {
        Set<ConstraintViolation<T>> violations = validator.validate(event);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
    }
}
