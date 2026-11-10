package com.Bank_website.rails.controller;

import com.Bank_website.rails.dto.ExternalPaymentResponse;
import com.Bank_website.rails.dto.InboundPaymentWebhookRequest;
import com.Bank_website.rails.security.QStashSignatureVerifier;
import com.Bank_website.rails.service.ExternalPaymentService;
import com.Bank_website.rails.service.RailProcessorService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/webhooks")
public class RailWebhookController {

    private final QStashSignatureVerifier verifier;
    private final ExternalPaymentService externalPaymentService;
    private final RailProcessorService railProcessorService;
    private final ObjectMapper objectMapper;
    private final String railSecret;

    public RailWebhookController(QStashSignatureVerifier verifier,
                                 ExternalPaymentService externalPaymentService,
                                 RailProcessorService railProcessorService,
                                 ObjectMapper objectMapper,
                                 @Value("${rail.secret:rail-secret-dev}") String railSecret) {
        this.verifier = verifier;
        this.externalPaymentService = externalPaymentService;
        this.railProcessorService = railProcessorService;
        this.objectMapper = objectMapper;
        this.railSecret = railSecret;
    }

    @PostMapping("/inbound-payment")
    public ResponseEntity<ExternalPaymentResponse> handleInboundPayment(
            @RequestHeader(value = "X-Rail-Secret", required = false) String secretHeader,
            @RequestHeader(value = "Upstash-Signature", required = false) String signatureHeader,
            @RequestBody String rawBody) throws JsonProcessingException {
        boolean authorized = (secretHeader != null && secretHeader.equals(railSecret))
                || verifier.verify(signatureHeader, rawBody != null ? rawBody : "");

        if (!authorized) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        InboundPaymentWebhookRequest request = objectMapper.readValue(rawBody, InboundPaymentWebhookRequest.class);
        validateInboundRequest(request);

        ExternalPaymentResponse response = externalPaymentService.processInboundPayment(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/rail-processor")
    public ResponseEntity<Map<String, Object>> processRailQueue(
            @RequestHeader(value = "Upstash-Signature", required = false) String signature,
            @RequestBody(required = false) String body) {
        String rawBody = body != null ? body : "";
        if (!verifier.verify(signature, rawBody)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(railProcessorService.processNextPendingPayment());
    }

    private void validateInboundRequest(InboundPaymentWebhookRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body cannot be null");
        }
        if (request.rail() == null || request.rail().isBlank()) {
            throw new IllegalArgumentException("rail is required");
        }
        if (request.toAccountNumber() == null || request.toAccountNumber().isBlank()) {
            throw new IllegalArgumentException("toAccountNumber is required");
        }
        if (request.externalSenderName() == null || request.externalSenderName().isBlank()) {
            throw new IllegalArgumentException("externalSenderName is required");
        }
        if (request.externalSenderAccount() == null || request.externalSenderAccount().isBlank()) {
            throw new IllegalArgumentException("externalSenderAccount is required");
        }
        if (request.externalRoutingNumber() == null || request.externalRoutingNumber().isBlank()) {
            throw new IllegalArgumentException("externalRoutingNumber is required");
        }
        if (request.amountCents() == null || request.amountCents() <= 0) {
            throw new IllegalArgumentException("amountCents must be greater than zero");
        }
    }
}
