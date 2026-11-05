package com.Bank_website.Novo_Bank_Service_PLC.controller;

import com.Bank_website.Novo_Bank_Service_PLC.dto.AmlRiskUpdateRequest;
import com.Bank_website.Novo_Bank_Service_PLC.dto.CreateCustomerRequest;
import com.Bank_website.Novo_Bank_Service_PLC.dto.CustomerResponse;
import com.Bank_website.Novo_Bank_Service_PLC.dto.KycUpdateRequest;
import com.Bank_website.Novo_Bank_Service_PLC.service.CustomerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @PostMapping
    public ResponseEntity<CustomerResponse> create(@Valid @RequestBody CreateCustomerRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(customerService.createCustomer(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CustomerResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(customerService.getById(id));
    }

    @GetMapping("/by-mobile/{mobileNo}")
    public ResponseEntity<CustomerResponse> getByMobile(@PathVariable String mobileNo) {
        return ResponseEntity.ok(customerService.getByMobileNo(mobileNo));
    }

    @GetMapping("/by-account/{accountId}")
    public ResponseEntity<CustomerResponse> getByAccount(@PathVariable UUID accountId) {
        return ResponseEntity.ok(customerService.getByAccountId(accountId));
    }

    @PatchMapping("/{id}/aml-risk")
    public ResponseEntity<CustomerResponse> updateAmlRisk(@PathVariable UUID id,
                                                          @Valid @RequestBody AmlRiskUpdateRequest request) {
        return ResponseEntity.ok(customerService.updateAmlRisk(id, request.amlRiskRating()));
    }

    @PatchMapping("/{id}/kyc")
    public ResponseEntity<CustomerResponse> updateKyc(@PathVariable UUID id,
                                                      @Valid @RequestBody KycUpdateRequest request) {
        return ResponseEntity.ok(customerService.updateKyc(id, request));
    }
}
