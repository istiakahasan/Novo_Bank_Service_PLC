package com.Bank_website.reporting.controller;

import com.Bank_website.reporting.dto.CreateRegulatoryReportRequest;
import com.Bank_website.reporting.dto.CreateStatementRequest;
import com.Bank_website.reporting.dto.PageResponse;
import com.Bank_website.reporting.dto.ReportJobResponse;
import com.Bank_website.reporting.exception.ForbiddenException;
import com.Bank_website.reporting.service.ReportJobService;
import com.Bank_website.security.AuthPrincipal;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportJobService reportJobService;

    public ReportController(ReportJobService reportJobService) {
        this.reportJobService = reportJobService;
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @PostMapping("/statement")
    public ResponseEntity<ReportJobResponse> requestStatement(@Valid @RequestBody CreateStatementRequest request,
                                                              Authentication authentication) {
        AuthPrincipal principal = AuthPrincipal.of(authentication);
        if (principal == null || principal.customerId() == null) {
            throw new ForbiddenException("Customer authentication required");
        }
        UUID requestedBy = parseUuid(principal.subject());
        ReportJobResponse response = reportJobService.enqueueCustomerStatement(principal.customerId(), request, requestedBy);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @PreAuthorize("hasAnyRole('CUSTOMER', 'STAFF')")
    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<ReportJobResponse> getJob(@PathVariable UUID jobId,
                                                    Authentication authentication) {
        AuthPrincipal principal = AuthPrincipal.of(authentication);
        boolean isStaff = principal != null && principal.roles().contains("STAFF");
        UUID customerId = principal != null ? principal.customerId() : null;
        return ResponseEntity.ok(reportJobService.getJob(jobId, customerId, isStaff));
    }

    @PreAuthorize("hasAnyRole('CUSTOMER', 'STAFF')")
    @GetMapping("/jobs")
    public ResponseEntity<PageResponse<ReportJobResponse>> listJobs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        AuthPrincipal principal = AuthPrincipal.of(authentication);
        boolean isStaff = principal != null && principal.roles().contains("STAFF");
        UUID customerId = principal != null ? principal.customerId() : null;
        if (!isStaff && customerId == null) {
            throw new ForbiddenException("Customer authentication required");
        }
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(reportJobService.listJobs(customerId, isStaff, pageable));
    }

    @PreAuthorize("hasRole('STAFF')")
    @PostMapping("/regulatory")
    public ResponseEntity<ReportJobResponse> requestRegulatoryReport(@Valid @RequestBody CreateRegulatoryReportRequest request,
                                                                    Authentication authentication) {
        AuthPrincipal principal = AuthPrincipal.of(authentication);
        UUID staffId = parseUuid(principal != null ? principal.subject() : null);
        ReportJobResponse response = reportJobService.enqueueRegulatoryReport(request, staffId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    private UUID parseUuid(String value) {
        if (value == null || value.length() != 36) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
