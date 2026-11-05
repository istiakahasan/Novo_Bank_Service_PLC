package com.Bank_website.support.controller;

import com.Bank_website.support.event.TransferCompletedWebhookHandler;
import com.Bank_website.support.security.QStashSignatureVerifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/webhooks")
public class SupportWebhookController {

    private final QStashSignatureVerifier verifier;
    private final TransferCompletedWebhookHandler handler;

    public SupportWebhookController(QStashSignatureVerifier verifier, TransferCompletedWebhookHandler handler) {
        this.verifier = verifier;
        this.handler = handler;
    }

    @PostMapping("/transfer-completed")
    public ResponseEntity<?> transferCompleted(
            @RequestHeader(value = "Upstash-Signature", required = false) String signature,
            @RequestBody String body) {
        // Signature first: an unsigned or forged request never reaches Redis, the customer API or the database.
        if (!verifier.verify(signature, body)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(handler.handle(body));
    }
}
