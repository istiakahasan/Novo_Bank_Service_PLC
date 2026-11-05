package com.banking.security;

import com.banking.security.util.JwtValidator;
import com.sun.net.httpserver.HttpServer;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class JwtValidatorTest {

    private final String publicKey = TestKeys.publicKeyBase64Der(TestKeys.PAIR);

    private JwtValidator validator(RedisTemplate<String, String> redis, boolean failOpen) {
        return new JwtValidator(publicKey, "banking-auth", "", failOpen, redis, Clock.systemUTC());
    }

    @Test
    void acceptsAValidTokenAndReturnsTheClaims() {
        UUID cid = UUID.randomUUID();
        Claims claims = validator(null, false).validate(TestKeys.token("CUSTOMER", cid));
        assertEquals("CUSTOMER", claims.get("role"));
        assertEquals(cid.toString(), claims.get("customerId"));
    }

    @Test
    void rejectsBadSignatureIssuerExpiryAlgorithmAndMissingJti() {
        JwtValidator v = validator(null, false);
        assertThrows(JwtException.class, () -> v.validate(TestKeys.token(TestKeys.OTHER, "banking-auth", "STAFF", null, 900, "j", "v1")));
        assertThrows(JwtException.class, () -> v.validate(TestKeys.token(TestKeys.PAIR, "someone-else", "STAFF", null, 900, "j", "v1")));
        assertThrows(JwtException.class, () -> v.validate(TestKeys.token(TestKeys.PAIR, "banking-auth", "STAFF", null, -3600, "j", "v1")));
        assertThrows(JwtException.class, () -> v.validate(TestKeys.token(TestKeys.PAIR, "banking-auth", "STAFF", null, 900, null, "v1")));
        assertThrows(JwtException.class, () -> v.validate("garbage"));
        assertThrows(JwtException.class, () -> v.validate(""));

        String unsigned = Jwts.builder().issuer("banking-auth").subject("x").id("j")
                .expiration(Date.from(Instant.now().plusSeconds(60))).compact();
        assertThrows(JwtException.class, () -> v.validate(unsigned));

        // classic algorithm-confusion attempt: HS256 signed with the public key bytes
        var hmacKey = io.jsonwebtoken.security.Keys.hmacShaKeyFor(TestKeys.PAIR.getPublic().getEncoded());
        String confused = Jwts.builder().issuer("banking-auth").subject("x").id("j")
                .expiration(Date.from(Instant.now().plusSeconds(60))).signWith(hmacKey).compact();
        assertThrows(RuntimeException.class, () -> v.validate(confused));
    }

    @Test
    void rejectsATamperedPayload() {
        String[] parts = TestKeys.token("CUSTOMER", UUID.randomUUID()).split("\\.");
        String forged = Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("{\"iss\":\"banking-auth\",\"sub\":\"" + UUID.randomUUID() + "\",\"role\":\"STAFF\",\"jti\":\"j\",\"exp\":9999999999}")
                        .getBytes(StandardCharsets.UTF_8));
        assertThrows(JwtException.class, () -> validator(null, false).validate(parts[0] + "." + forged + "." + parts[2]));
    }

    @Test
    void blocklistedTokensAreRejected() {
        RedisTemplate<String, String> redis = mock(RedisTemplate.class);
        String jti = UUID.randomUUID().toString();
        String token = TestKeys.token(TestKeys.PAIR, "banking-auth", "STAFF", null, 900, jti, "v1");

        when(redis.hasKey("jwt_blocklist:" + jti)).thenReturn(false);
        validator(redis, false).validate(token);

        when(redis.hasKey("jwt_blocklist:" + jti)).thenReturn(true);
        JwtException e = assertThrows(JwtException.class, () -> validator(redis, false).validate(token));
        assertTrue(e.getMessage().contains("revoked"));
    }

    @Test
    void aRedisOutageFailsClosedUnlessConfiguredOtherwise() {
        RedisTemplate<String, String> redis = mock(RedisTemplate.class);
        when(redis.hasKey(org.mockito.ArgumentMatchers.anyString())).thenThrow(new RedisConnectionFailureException("down"));
        String token = TestKeys.token("STAFF", null);
        assertThrows(JwtException.class, () -> validator(redis, false).validate(token));
        assertEquals("STAFF", validator(redis, true).validate(token).get("role"));
    }

    @Test
    void withoutARedisTemplateTheBlocklistIsSkipped() {
        assertEquals("STAFF", validator(null, false).validate(TestKeys.token("STAFF", null)).get("role"));
    }

    @Test
    void publicKeyAcceptsDerPemAndBase64Pem() {
        String der = publicKey;
        String pem = "-----BEGIN PUBLIC KEY-----\n" + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(TestKeys.PAIR.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----\n";
        String token = TestKeys.token("STAFF", null);
        for (String format : new String[]{der, pem, Base64.getEncoder().encodeToString(pem.getBytes())}) {
            new JwtValidator(format, "banking-auth", "", false, null, Clock.systemUTC()).validate(token);
        }
        assertThrows(IllegalStateException.class, () -> new JwtValidator("not a key", "banking-auth", "", false, null, Clock.systemUTC()));
    }

    @Test
    void jwksAreFetchedOnceCachedForAnHourAndRefetchedAfterwards() throws Exception {
        AtomicInteger hits = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/auth/.well-known/jwks.json", exchange -> {
            hits.incrementAndGet();
            byte[] body = TestKeys.modulusJwks(TestKeys.PAIR, "v1").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            String authUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/";
            MutableClock clock = new MutableClock(Instant.now());
            JwtValidator v = new JwtValidator("", "banking-auth", authUrl, false, null, clock);
            String token = longLived("CUSTOMER", "v1");

            v.validate(token);
            v.validate(token);
            assertEquals(1, hits.get());

            clock.advance(Duration.ofMinutes(59));
            v.validate(token);
            assertEquals(1, hits.get());

            clock.advance(Duration.ofMinutes(2)); // past one hour
            v.validate(longLived("CUSTOMER", "v1"));
            assertEquals(2, hits.get());

            // a token naming an unknown kid forces at most one re-fetch per minute
            String rotated = longLived("STAFF", "v2");
            clock.advance(Duration.ofMinutes(2));
            assertThrows(JwtException.class, () -> v.validate(rotated));
            assertEquals(3, hits.get());
            assertThrows(JwtException.class, () -> v.validate(rotated));
            assertEquals(3, hits.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void tokensAreRejectedWhenNoKeySourceIsReachable() {
        JwtValidator v = new JwtValidator("", "banking-auth", "http://127.0.0.1:1", false, null, Clock.systemUTC());
        assertThrows(JwtException.class, () -> v.validate(TestKeys.token("STAFF", null)));
        JwtValidator none = new JwtValidator("", "banking-auth", "", false, null, Clock.systemUTC());
        assertThrows(JwtException.class, () -> none.validate(TestKeys.token("STAFF", null)));
    }

    private static String longLived(String role, String kid) {
        return TestKeys.token(TestKeys.PAIR, "banking-auth", role, null, 7 * 24 * 3600, UUID.randomUUID().toString(), kid);
    }

    static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
