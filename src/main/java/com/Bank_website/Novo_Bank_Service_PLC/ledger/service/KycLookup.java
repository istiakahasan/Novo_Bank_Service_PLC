package com.Bank_website.Novo_Bank_Service_PLC.ledger.service;

import java.util.UUID;

/**
 * Source of a customer's KYC status. Two implementations:
 * {@link LocalKycLookup} (default: Customer and Ledger share one JVM) and
 * {@link HttpKycLookup} (when customer.service.url is set, i.e. the services are deployed separately).
 */
public interface KycLookup {

    /** @throws com.Bank_website.Novo_Bank_Service_PLC.ledger.exception.ResourceNotFoundException unknown account
     *  @throws com.Bank_website.Novo_Bank_Service_PLC.ledger.exception.ServiceUnavailableException dependency down */
    String statusByAccount(UUID accountId);

    String statusByCustomer(UUID customerId);
}
