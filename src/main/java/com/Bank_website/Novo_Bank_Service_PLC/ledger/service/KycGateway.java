package com.Bank_website.Novo_Bank_Service_PLC.ledger.service;

import com.Bank_website.Novo_Bank_Service_PLC.dto.CustomerResponse;
import com.Bank_website.Novo_Bank_Service_PLC.ledger.exception.KycNotVerifiedException;
import com.Bank_website.Novo_Bank_Service_PLC.ledger.exception.ResourceNotFoundException;
import com.Bank_website.Novo_Bank_Service_PLC.service.CustomerService;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * Ledger-side KYC gate. Customer and Ledger run in the same JVM, so the customer lookup is an
 * in-process call to CustomerService (the logic behind GET /api/customers/{id}) instead of an HTTP hop.
 *
 * Only "verified" is cached (Redis kyc:{customerId}, 600 s). Caching "pending" would keep rejecting
 * a customer for up to 10 minutes after their KYC is approved.
 */
@Component
public class KycGateway {

    static final Duration CACHE_TTL = Duration.ofSeconds(600);

    private final CustomerService customerService;
    private final RedisTemplate<String, String> redis;

    public KycGateway(CustomerService customerService, RedisTemplate<String, String> redis) {
        this.customerService = customerService;
        this.redis = redis;
    }

    public void requireVerified(UUID customerId) {
        String key = "kyc:" + customerId;
        if ("verified".equals(redis.opsForValue().get(key))) {
            return;
        }
        CustomerResponse customer;
        try {
            customer = customerService.getById(customerId);
        } catch (com.Bank_website.Novo_Bank_Service_PLC.exception.ResourceNotFoundException e) {
            throw new ResourceNotFoundException("Customer not found: " + customerId);
        }
        if (!"verified".equals(customer.kycStatus())) {
            throw new KycNotVerifiedException("Customer KYC is not verified (status: " + customer.kycStatus() + ")");
        }
        redis.opsForValue().set(key, "verified", CACHE_TTL);
    }
}
