package com.Bank_website.rails.controller;

import com.Bank_website.rails.dto.CreateOutboundPaymentRequest;
import com.Bank_website.rails.dto.ExternalPaymentResponse;
import com.Bank_website.rails.dto.PageResponse;
import com.Bank_website.rails.service.ExternalPaymentService;
import com.Bank_website.security.AuthPrincipal;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/external-payments")
public class ExternalPaymentController {

    private final ExternalPaymentService externalPaymentService;

    public ExternalPaymentController(ExternalPaymentService externalPaymentService) {
        this.externalPaymentService = externalPaymentService;
    }

    @PostMapping("/outbound")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ExternalPaymentResponse> createOutboundPayment(@Valid @RequestBody CreateOutboundPaymentRequest request,
                                                                         Authentication authentication) {
        AuthPrincipal principal = AuthPrincipal.of(authentication);
        ExternalPaymentResponse response = externalPaymentService.createOutboundPayment(principal.customerId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'STAFF')")
    public ResponseEntity<ExternalPaymentResponse> getPayment(@PathVariable UUID id,
                                                              Authentication authentication) {
        AuthPrincipal principal = AuthPrincipal.of(authentication);
        ExternalPaymentResponse response = externalPaymentService.getPaymentById(id, principal);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('CUSTOMER', 'STAFF')")
    public ResponseEntity<PageResponse<ExternalPaymentResponse>> listPayments(@RequestParam(defaultValue = "0") int page,
                                                                              @RequestParam(defaultValue = "20") int size,
                                                                              Authentication authentication) {
        AuthPrincipal principal = AuthPrincipal.of(authentication);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        PageResponse<ExternalPaymentResponse> response = externalPaymentService.listPayments(principal, pageable);
        return ResponseEntity.ok(response);
    }
}
