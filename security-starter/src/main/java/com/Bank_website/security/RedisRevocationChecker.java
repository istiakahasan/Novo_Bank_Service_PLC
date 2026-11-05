package com.Bank_website.security;

import org.springframework.data.redis.core.StringRedisTemplate;

/** One Redis EXISTS per authenticated request. */
public class RedisRevocationChecker implements RevocationChecker {

    private final StringRedisTemplate redis;
    private final boolean failOpen;

    public RedisRevocationChecker(StringRedisTemplate redis, boolean failOpen) {
        this.redis = redis;
        this.failOpen = failOpen;
    }

    @Override
    public boolean isRevoked(String jti) {
        if (jti == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(redis.hasKey(KEY_PREFIX + jti));
        } catch (RuntimeException e) {
            if (failOpen) {
                return false;
            }
            throw new IllegalStateException("Cannot check token revocation status", e);
        }
    }
}
