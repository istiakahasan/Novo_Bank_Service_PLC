package com.Bank_website.Novo_Bank_Service_PLC.ledger.service;

import com.Bank_website.Novo_Bank_Service_PLC.ledger.exception.KycNotVerifiedException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * Ledger-side KYC gate with a Redis cache in front of a {@link KycLookup}.
 *
 * Keys: kyc:{accountId} for transfers, kyc:customer:{customerId} for account opening; TTL 600 s.
 * Only "verified" is cached. Caching "pending" would keep rejecting a customer for up to
 * 10 minutes after their KYC is approved.
 */
@Component
public class KycGateway {

    static final Duration CACHE_TTL = Duration.ofSeconds(600);

    private final KycLookup lookup;
    private final RedisTemplate<String, String> redis;

    public KycGateway(KycLookup lookup, RedisTemplate<String, String> redis) {
        this.lookup = lookup;
        this.redis = redis;
    }

    /** Transfer path: 404 unknown account, 422 not verified, 503 customer service unavailable. */
    public void requireVerifiedAccount(UUID accountId) {
        check("kyc:" + accountId, () -> lookup.statusByAccount(accountId));
    }

    /** Account-opening path. */
    public void requireVerifiedCustomer(UUID customerId) {
        check("kyc:customer:" + customerId, () -> lookup.statusByCustomer(customerId));
    }

    private void check(String cacheKey, java.util.function.Supplier<String> status) {
        if ("verified".equals(redis.opsForValue().get(cacheKey))) {
            return;
        }
        String kycStatus = status.get();
        if (!"verified".equals(kycStatus)) {
            throw new KycNotVerifiedException("Customer KYC is not verified (status: " + kycStatus + ")");
        }
        redis.opsForValue().set(cacheKey, "verified", CACHE_TTL);
    }
}
