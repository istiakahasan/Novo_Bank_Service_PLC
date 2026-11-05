package com.Bank_website.notification.service;

import com.Bank_website.notification.dto.CustomerInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/** Looks customers up in the Customer Service and caches them in Redis for 600 s. */
@Service
public class CustomerLookupService {

    private static final Duration CACHE_TTL = Duration.ofSeconds(600);

    private final RestClient customerClient;
    private final RedisTemplate<String, String> redis;
    private final ObjectMapper objectMapper;

    public CustomerLookupService(@Qualifier("customerServiceClient") RestClient customerClient,
                                 RedisTemplate<String, String> redis,
                                 ObjectMapper objectMapper) {
        this.customerClient = customerClient;
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public Optional<CustomerInfo> findByAccount(UUID accountId) {
        return lookup("cust:acct:" + accountId, "/api/customers/by-account/{v}", accountId);
    }

    public Optional<CustomerInfo> findByMobile(String mobileNo) {
        return lookup("cust:mobile:" + mobileNo, "/api/customers/by-mobile/{v}", mobileNo);
    }

    /** 404 means "no such customer" (empty); any other failure propagates so QStash retries. */
    private Optional<CustomerInfo> lookup(String cacheKey, String uriTemplate, Object variable) {
        String cached = redis.opsForValue().get(cacheKey);
        if (cached != null) {
            return Optional.of(read(cached));
        }
        CustomerInfo customer;
        try {
            customer = customerClient.get().uri(uriTemplate, variable).retrieve().body(CustomerInfo.class);
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        }
        if (customer == null) {
            return Optional.empty();
        }
        redis.opsForValue().set(cacheKey, write(customer), CACHE_TTL);
        return Optional.of(customer);
    }

    private String write(CustomerInfo info) {
        try {
            return objectMapper.writeValueAsString(info);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot cache customer", e);
        }
    }

    private CustomerInfo read(String json) {
        try {
            return objectMapper.readValue(json, CustomerInfo.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Corrupt customer cache entry", e);
        }
    }
}
