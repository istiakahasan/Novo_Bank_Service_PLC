package com.Bank_website.loan.controller;

import com.Bank_website.loan.dto.CreateLoanApplicationRequest;
import com.Bank_website.loan.dto.LoanApplicationResponse;
import com.Bank_website.loan.dto.LoanProductResponse;
import com.Bank_website.loan.dto.PageResponse;
import com.Bank_website.loan.dto.RepaymentResponse;
import com.Bank_website.loan.dto.ReviewLoanRequest;
import com.Bank_website.loan.exception.ForbiddenException;
import com.Bank_website.loan.service.LoanApplicationService;
import com.Bank_website.loan.service.LoanDisbursementService;
import com.Bank_website.loan.service.LoanRepaymentService;
import com.Bank_website.security.AuthPrincipal;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/loans")
public class LoanController {

    private final LoanApplicationService loanApplicationService;
    private final LoanDisbursementService loanDisbursementService;
    private final LoanRepaymentService loanRepaymentService;

    public LoanController(LoanApplicationService loanApplicationService,
                          LoanDisbursementService loanDisbursementService,
                          LoanRepaymentService loanRepaymentService) {
        this.loanApplicationService = loanApplicationService;
        this.loanDisbursementService = loanDisbursementService;
        this.loanRepaymentService = loanRepaymentService;
    }

    @GetMapping("/products")
    public ResponseEntity<List<LoanProductResponse>> getProducts() {
        return ResponseEntity.ok(loanApplicationService.getActiveProducts());
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @PostMapping("/applications")
    public ResponseEntity<LoanApplicationResponse> createApplication(
            @Valid @RequestBody CreateLoanApplicationRequest request,
            Authentication authentication) {
        AuthPrincipal principal = AuthPrincipal.of(authentication);
        if (principal == null || principal.customerId() == null) {
            throw new ForbiddenException("Authentication required with valid customer identity");
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(loanApplicationService.createApplication(principal.customerId(), request));
    }

    @PreAuthorize("hasAnyRole('CUSTOMER', 'STAFF')")
    @GetMapping("/applications")
    public ResponseEntity<PageResponse<LoanApplicationResponse>> listApplications(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String productCode,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        AuthPrincipal principal = AuthPrincipal.of(authentication);
        Pageable pageable = PageRequest.of(page, size);
        if (principal != null && principal.roles().contains("STAFF")) {
            Page<LoanApplicationResponse> applications = loanApplicationService.getApplicationsForStaff(status, productCode, pageable);
            return ResponseEntity.ok(PageResponse.from(applications));
        }
        if (principal == null || principal.customerId() == null) {
            throw new ForbiddenException("Authentication required with valid customer identity");
        }
        Page<LoanApplicationResponse> applications = loanApplicationService.getApplicationsForCustomer(principal.customerId(), pageable);
        return ResponseEntity.ok(PageResponse.from(applications));
    }

    @PreAuthorize("hasAnyRole('CUSTOMER', 'STAFF')")
    @GetMapping("/applications/{id}")
    public ResponseEntity<LoanApplicationResponse> getApplication(
            @PathVariable UUID id,
            Authentication authentication) {
        AuthPrincipal principal = AuthPrincipal.of(authentication);
        boolean isStaff = principal != null && principal.roles().contains("STAFF");
        UUID callerCustomerId = principal != null ? principal.customerId() : null;
        return ResponseEntity.ok(loanApplicationService.getApplication(id, callerCustomerId, isStaff));
    }

    @PreAuthorize("hasRole('STAFF')")
    @PatchMapping("/applications/{id}/review")
    public ResponseEntity<LoanApplicationResponse> reviewApplication(
            @PathVariable UUID id,
            @Valid @RequestBody ReviewLoanRequest request,
            Authentication authentication) {
        AuthPrincipal principal = AuthPrincipal.of(authentication);
        String reviewerSubject = principal != null ? principal.subject() : null;
        return ResponseEntity.ok(loanApplicationService.reviewApplication(id, reviewerSubject, request));
    }

    @PreAuthorize("hasRole('STAFF')")
    @PostMapping("/applications/{id}/disburse")
    public ResponseEntity<LoanApplicationResponse> disburseLoan(@PathVariable UUID id) {
        return ResponseEntity.ok(loanDisbursementService.disburseLoan(id));
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @PostMapping("/repayments/{id}/pay")
    public ResponseEntity<RepaymentResponse> payRepayment(
            @PathVariable UUID id,
            Authentication authentication) {
        AuthPrincipal principal = AuthPrincipal.of(authentication);
        if (principal == null || principal.customerId() == null) {
            throw new ForbiddenException("Authentication required with valid customer identity");
        }
        return ResponseEntity.ok(loanRepaymentService.payRepayment(id, principal.customerId()));
    }
}
