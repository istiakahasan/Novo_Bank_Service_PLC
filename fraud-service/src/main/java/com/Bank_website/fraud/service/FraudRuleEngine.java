package com.Bank_website.fraud.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.UUID;

@Service
public class FraudRuleEngine {

    private static final Logger log = LoggerFactory.getLogger(FraudRuleEngine.class);
    private static final Duration RISK_CACHE_TTL = Duration.ofSeconds(300);
    private static final Duration PROCESSED_TTL = Duration.ofHours(24);

    private final RedisTemplate<String, String> redis;
    private final RestClient customerClient;
    private final long highValueThresholdCents;
    private final long velocityMaxTransactions;
    private final Duration velocityWindow;

    public FraudRuleEngine(RedisTemplate<String, String> redis,
                           @Qualifier("customerServiceClient") RestClient customerClient,
                           @Value("${fraud.high-value-threshold-cents:500000}") long highValueThresholdCents,
                           @Value("${fraud.velocity-max-transactions:10}") long velocityMaxTransactions,
                           @Value("${fraud.velocity-window-seconds:60}") long velocityWindowSeconds) {
        this.redis = redis;
        this.customerClient = customerClient;
        this.highValueThresholdCents = highValueThresholdCents;
        this.velocityMaxTransactions = velocityMaxTransactions;
        this.velocityWindow = Duration.ofSeconds(velocityWindowSeconds);
    }

    /** RULE 1: strictly more than $5,000. */
    public boolean checkHighValue(Long amountCents) {
        return amountCents != null && amountCents > highValueThresholdCents;
    }

    /** RULE 2: more than 10 transfers from this account within the current 60 s window. */
    public boolean checkVelocity(UUID accountId) {
        String value = redis.opsForValue().get(velocityKey(accountId));
        return value != null && Long.parseLong(value) > velocityMaxTransactions;
    }

    /**
     * RULE 3: customer's aml_risk_rating is 'high'. The rating is cached for 300 s.
     * Fails OPEN (returns false) if the Customer Service is unreachable so rules 1 and 2
     * still produce alerts instead of the whole webhook failing.
     */
    public boolean checkCustomerRisk(UUID accountId) {
        String cacheKey = "cust_risk:" + accountId;
        String rating = redis.opsForValue().get(cacheKey);
        if (rating == null) {
            try {
                JsonNode customer = customerClient.get()
                        .uri("/api/customers/by-account/{id}", accountId)
                        .retrieve()
                        .body(JsonNode.class);
                rating = customer == null ? null : customer.path("amlRiskRating").asText(null);
            } catch (Exception e) {
                log.warn("Customer lookup failed for account {}: {}", accountId, e.getMessage());
                return false;
            }
            if (rating == null) {
                return false;
            }
            redis.opsForValue().set(cacheKey, rating, RISK_CACHE_TTL);
        }
        return "high".equalsIgnoreCase(rating);
    }

    /** Counts this transfer in the account's velocity window (INCR, EXPIRE on first hit). */
    public void recordTransaction(UUID accountId) {
        String key = velocityKey(accountId);
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redis.expire(key, velocityWindow);
        }
    }

    /** True if this transaction id was already fully processed (QStash retry / duplicate delivery). */
    public boolean isProcessed(UUID transactionId) {
        return Boolean.TRUE.equals(redis.hasKey("fraud_done:" + transactionId));
    }

    public void markProcessed(UUID transactionId) {
        redis.opsForValue().set("fraud_done:" + transactionId, "1", PROCESSED_TTL);
    }

    private String velocityKey(UUID accountId) {
        return "txn_count:" + accountId;
    }
}
