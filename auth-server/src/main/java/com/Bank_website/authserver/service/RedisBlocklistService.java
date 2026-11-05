package com.Bank_website.authserver.service;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Access-token revocation list.
 * jwt_blocklist:{jti}  is the key of this service.
 * revoked:jti:{jti}    is the key the shared security-starter (the other services) checks, so a logout takes effect everywhere.
 * Both use TTL = remaining token lifetime, so the list cleans itself.
 */
@Component
public class RedisBlocklistService {

    static final String KEY_PREFIX = "jwt_blocklist:";
    static final String STARTER_KEY_PREFIX = "revoked:jti:";

    private final RedisTemplate<String, String> redis;

    public RedisBlocklistService(RedisTemplate<String, String> redis) {
        this.redis = redis;
    }

    public void blocklist(String jti, long ttlSeconds) {
        if (jti == null || ttlSeconds <= 0) {
            return; // already expired: nothing to block
        }
        Duration ttl = Duration.ofSeconds(ttlSeconds);
        redis.opsForValue().set(KEY_PREFIX + jti, "1", ttl);
        redis.opsForValue().set(STARTER_KEY_PREFIX + jti, "1", ttl);
    }

    public boolean isBlocklisted(String jti) {
        return jti != null && Boolean.TRUE.equals(redis.hasKey(KEY_PREFIX + jti));
    }
}
