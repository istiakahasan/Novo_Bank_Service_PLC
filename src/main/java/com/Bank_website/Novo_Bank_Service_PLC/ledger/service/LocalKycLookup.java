package com.Bank_website.Novo_Bank_Service_PLC.ledger.service;

import com.Bank_website.Novo_Bank_Service_PLC.ledger.exception.ResourceNotFoundException;
import com.Bank_website.Novo_Bank_Service_PLC.service.CustomerService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** In-process lookup: same logic as GET /api/customers/by-account/{id}, without the HTTP hop. */
@Component
@ConditionalOnExpression("'${customer.service.url:}'.isEmpty()")
public class LocalKycLookup implements KycLookup {

    private final CustomerService customerService;

    public LocalKycLookup(CustomerService customerService) {
        this.customerService = customerService;
    }

    @Override
    public String statusByAccount(UUID accountId) {
        try {
            return customerService.getByAccountId(accountId).kycStatus();
        } catch (com.Bank_website.Novo_Bank_Service_PLC.exception.ResourceNotFoundException e) {
            throw new ResourceNotFoundException(e.getMessage());
        }
    }

    @Override
    public String statusByCustomer(UUID customerId) {
        try {
            return customerService.getById(customerId).kycStatus();
        } catch (com.Bank_website.Novo_Bank_Service_PLC.exception.ResourceNotFoundException e) {
            throw new ResourceNotFoundException(e.getMessage());
        }
    }
}
