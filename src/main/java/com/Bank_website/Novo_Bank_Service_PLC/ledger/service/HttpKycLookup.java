package com.Bank_website.Novo_Bank_Service_PLC.ledger.service;

import com.Bank_website.Novo_Bank_Service_PLC.ledger.exception.ResourceNotFoundException;
import com.Bank_website.Novo_Bank_Service_PLC.ledger.exception.ServiceUnavailableException;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.UUID;

/**
 * HTTP lookup against a separately deployed Customer Service (customer.service.url set).
 * Any timeout (5 s), connection error or 5xx becomes a 503: the transfer is never attempted
 * without a KYC answer, e.g. while a free-tier Render instance is waking up.
 */
@Component
@ConditionalOnExpression("!'${customer.service.url:}'.isEmpty()")
public class HttpKycLookup implements KycLookup {

    private final RestClient client;

    public HttpKycLookup(@Qualifier("ledgerCustomerClient") RestClient client) {
        this.client = client;
    }

    @Override
    public String statusByAccount(UUID accountId) {
        return fetch("/api/customers/by-account/{id}", accountId, "Account not found: " + accountId);
    }

    @Override
    public String statusByCustomer(UUID customerId) {
        return fetch("/api/customers/{id}", customerId, "Customer not found: " + customerId);
    }

    private String fetch(String uri, UUID id, String notFoundMessage) {
        try {
            JsonNode body = client.get().uri(uri, id).retrieve().body(JsonNode.class);
            if (body == null || body.path("kycStatus").isMissingNode()) {
                throw new ServiceUnavailableException("Customer service returned an unexpected response");
            }
            return body.path("kycStatus").asText();
        } catch (HttpClientErrorException.NotFound e) {
            throw new ResourceNotFoundException(notFoundMessage);
        } catch (RestClientException e) {
            throw new ServiceUnavailableException("Customer service unavailable, try again shortly", e);
        }
    }
}
