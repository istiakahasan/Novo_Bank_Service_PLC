package com.Bank_website.loan.controller;

import com.Bank_website.loan.security.QStashSignatureVerifier;
import com.Bank_website.loan.service.LoanRepaymentService;
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
public class LoanWebhookController {

    private final QStashSignatureVerifier verifier;
    private final LoanRepaymentService loanRepaymentService;

    public LoanWebhookController(QStashSignatureVerifier verifier, LoanRepaymentService loanRepaymentService) {
        this.verifier = verifier;
        this.loanRepaymentService = loanRepaymentService;
    }

    @PostMapping("/loan-schedule")
    public ResponseEntity<Map<String, Object>> handleLoanSchedule(
            @RequestHeader(value = "Upstash-Signature", required = false) String signature,
            @RequestBody(required = false) String body) {
        String rawBody = body != null ? body : "";
        if (!verifier.verify(signature, rawBody)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        int overdueCount = loanRepaymentService.markOverdueRepayments();
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "overdueRepaymentsMarked", overdueCount));
    }
}
