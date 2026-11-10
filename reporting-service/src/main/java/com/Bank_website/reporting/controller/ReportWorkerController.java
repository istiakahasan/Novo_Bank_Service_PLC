package com.Bank_website.reporting.controller;

import com.Bank_website.reporting.security.QStashSignatureVerifier;
import com.Bank_website.reporting.service.ReportJobService;
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
public class ReportWorkerController {

    private final QStashSignatureVerifier verifier;
    private final ReportJobService reportJobService;

    public ReportWorkerController(QStashSignatureVerifier verifier, ReportJobService reportJobService) {
        this.verifier = verifier;
        this.reportJobService = reportJobService;
    }

    @PostMapping("/report-worker")
    public ResponseEntity<Map<String, Object>> processNextJob(
            @RequestHeader(value = "Upstash-Signature", required = false) String signature,
            @RequestBody(required = false) String body) {
        String rawBody = body != null ? body : "";
        if (!verifier.verify(signature, rawBody)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(reportJobService.processNext());
    }
}
