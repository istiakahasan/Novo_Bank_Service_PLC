package com.Bank_website.cards.controller;

import com.Bank_website.cards.security.QStashSignatureVerifier;
import com.Bank_website.cards.service.CardSettlementService;
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
public class CardWebhookController {

    private final QStashSignatureVerifier verifier;
    private final CardSettlementService cardSettlementService;

    public CardWebhookController(QStashSignatureVerifier verifier,
                                 CardSettlementService cardSettlementService) {
        this.verifier = verifier;
        this.cardSettlementService = cardSettlementService;
    }

    @PostMapping("/card-batch")
    public ResponseEntity<Map<String, Object>> handleCardBatch(
            @RequestHeader(value = "Upstash-Signature", required = false) String signature,
            @RequestBody(required = false) String body) {
        String rawBody = body != null ? body : "";
        if (!verifier.verify(signature, rawBody)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(cardSettlementService.processBatch());
    }

    @PostMapping("/card-clear")
    public ResponseEntity<Map<String, Object>> handleCardClear(
            @RequestHeader(value = "Upstash-Signature", required = false) String signature,
            @RequestBody(required = false) String body) {
        String rawBody = body != null ? body : "";
        if (!verifier.verify(signature, rawBody)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(cardSettlementService.processClear());
    }

    @PostMapping("/card-fund")
    public ResponseEntity<Map<String, Object>> handleCardFund(
            @RequestHeader(value = "Upstash-Signature", required = false) String signature,
            @RequestBody(required = false) String body) {
        String rawBody = body != null ? body : "";
        if (!verifier.verify(signature, rawBody)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(cardSettlementService.processFund());
    }
}
