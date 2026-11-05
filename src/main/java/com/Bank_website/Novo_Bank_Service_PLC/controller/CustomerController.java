package com.Bank_website.Novo_Bank_Service_PLC.controller;

import com.Bank_website.Novo_Bank_Service_PLC.dto.AccountSummaryResponse;
import com.Bank_website.Novo_Bank_Service_PLC.dto.AmlRiskUpdateRequest;
import com.Bank_website.Novo_Bank_Service_PLC.dto.CreateCustomerRequest;
import com.Bank_website.Novo_Bank_Service_PLC.dto.CustomerResponse;
import com.Bank_website.Novo_Bank_Service_PLC.dto.KycUpdateRequest;
import com.Bank_website.Novo_Bank_Service_PLC.dto.PaginatedCustomerResponse;
import com.Bank_website.Novo_Bank_Service_PLC.exception.ForbiddenException;
import com.Bank_website.Novo_Bank_Service_PLC.service.CustomerService;
import com.Bank_website.security.AuthPrincipal;
import jakarta.validation.Valid;
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
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @PreAuthorize("hasRole('STAFF')")
    @PostMapping
    public ResponseEntity<CustomerResponse> create(@Valid @RequestBody CreateCustomerRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(customerService.createCustomer(request));
    }

    /** STAFF only. Filters (case-insensitive): kycStatus PENDING/VERIFIED/REJECTED/FLAGGED, status, riskTier LOW/MEDIUM/HIGH. */
    @PreAuthorize("hasRole('STAFF')")
    @GetMapping
    public ResponseEntity<PaginatedCustomerResponse> list(@RequestParam(required = false) String kycStatus,
                                                          @RequestParam(required = false) String status,
                                                          @RequestParam(required = false) String riskTier,
                                                          @RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(customerService.listCustomers(kycStatus, status, riskTier, page, size));
    }

    /** CUSTOMER only: the caller's own profile, taken from the token (no path or query parameter). */
    @PreAuthorize("hasRole('CUSTOMER')")
    @GetMapping("/me")
    public ResponseEntity<CustomerResponse> me(Authentication authentication) {
        AuthPrincipal principal = AuthPrincipal.of(authentication);
        if (principal == null || principal.customerId() == null) {
            throw new ForbiddenException("Token has no customer id");
        }
        return ResponseEntity.ok(customerService.getById(principal.customerId()));
    }

    @PreAuthorize("hasAnyRole('STAFF','SYSTEM') or (hasRole('CUSTOMER') and @access.isOwnCustomer(authentication, #id))")
    @GetMapping("/{id}")
    public ResponseEntity<CustomerResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(customerService.getById(id));
    }

    /** CUSTOMER (own id only, else 403) or STAFF. Aggregated from the Ledger Service, cached 60 s. */
    @PreAuthorize("hasRole('STAFF') or (hasRole('CUSTOMER') and @access.isOwnCustomer(authentication, #id))")
    @GetMapping("/{id}/accounts")
    public ResponseEntity<List<AccountSummaryResponse>> accounts(@PathVariable UUID id) {
        return ResponseEntity.ok(customerService.getAccounts(id));
    }

    @PreAuthorize("hasAnyRole('STAFF','SYSTEM')")
    @GetMapping("/by-mobile/{mobileNo}")
    public ResponseEntity<CustomerResponse> getByMobile(@PathVariable String mobileNo) {
        return ResponseEntity.ok(customerService.getByMobileNo(mobileNo));
    }

    @PreAuthorize("hasAnyRole('STAFF','SYSTEM') or (hasRole('CUSTOMER') and @access.ownsAccount(authentication, #accountId))")
    @GetMapping("/by-account/{accountId}")
    public ResponseEntity<CustomerResponse> getByAccount(@PathVariable UUID accountId) {
        return ResponseEntity.ok(customerService.getByAccountId(accountId));
    }

    @PreAuthorize("hasAnyRole('STAFF','SYSTEM')")
    @PatchMapping("/{id}/aml-risk")
    public ResponseEntity<CustomerResponse> updateAmlRisk(@PathVariable UUID id,
                                                          @Valid @RequestBody AmlRiskUpdateRequest request) {
        return ResponseEntity.ok(customerService.updateAmlRisk(id, request.amlRiskRating()));
    }

    @PreAuthorize("hasRole('STAFF')")
    @PatchMapping("/{id}/kyc")
    public ResponseEntity<CustomerResponse> updateKyc(@PathVariable UUID id,
                                                      @Valid @RequestBody KycUpdateRequest request) {
        return ResponseEntity.ok(customerService.updateKyc(id, request));
    }
}
