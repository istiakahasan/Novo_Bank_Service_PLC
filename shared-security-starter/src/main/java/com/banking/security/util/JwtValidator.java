package com.banking.security.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.LocatorAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Validates access tokens issued by the Auth Service: RS256 signature, exp, iss == jwt.issuer (default "banking-auth"),
 * a jti, and not present in the Redis blocklist (key "jwt_blocklist:{jti}").
 *
 * Key source: jwt.public-key / JWT_PUBLIC_KEY (PEM, base64 of the PEM, or base64 of the DER), otherwise the JWKS at
 * {auth.url}/api/auth/.well-known/jwks.json, cached for one hour (re-fetched at most once a minute when a token
 * names an unknown kid, which is how a key rotation is picked up).
 *
 * The blocklist needs a RedisTemplate<String,String> bean from the consuming service; without one the blocklist
 * check is skipped and a warning is logged once.
 */
@Component
public class JwtValidator {

    private static final Logger log = LoggerFactory.getLogger(JwtValidator.class);
    static final String BLOCKLIST_PREFIX = "jwt_blocklist:";
    static final Duration JWKS_TTL = Duration.ofHours(1);
    static final Duration JWKS_MIN_REFETCH = Duration.ofMinutes(1);

    private final String issuer;
    private final String authUrl;
    private final boolean blocklistFailOpen;
    private final PublicKey staticKey;
    private final RedisTemplate<String, String> redis;
    private final Clock clock;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicBoolean warnedNoRedis = new AtomicBoolean(false);

    private volatile Map<String, PublicKey> jwks = Map.of();
    private volatile Instant jwksFetchedAt = Instant.EPOCH;
    private volatile Instant jwksAttemptedAt = Instant.EPOCH;

    @Autowired
    public JwtValidator(@Value("${jwt.public-key:${JWT_PUBLIC_KEY:}}") String publicKey,
                        @Value("${jwt.issuer:banking-auth}") String issuer,
                        @Value("${auth.url:${AUTH_URL:}}") String authUrl,
                        @Value("${jwt.blocklist-fail-open:false}") boolean blocklistFailOpen,
                        ObjectProvider<RedisTemplate<String, String>> redisProvider) {
        this(publicKey, issuer, authUrl, blocklistFailOpen, redisProvider.orderedStream().findFirst().orElse(null), Clock.systemUTC());
    }

    public JwtValidator(String publicKey, String issuer, String authUrl, boolean blocklistFailOpen,
                        RedisTemplate<String, String> redis, Clock clock) {
        this.issuer = issuer;
        this.authUrl = authUrl == null ? "" : authUrl.replaceAll("/+$", "");
        this.blocklistFailOpen = blocklistFailOpen;
        this.redis = redis;
        this.clock = clock;
        this.staticKey = publicKey == null || publicKey.isBlank() ? null : parsePublicKey(publicKey);
        if (staticKey == null && this.authUrl.isEmpty()) {
            log.warn("Neither jwt.public-key (JWT_PUBLIC_KEY) nor auth.url (AUTH_URL) is set: every token will be rejected");
        }
    }

    /** @throws JwtException if the token is malformed, unsigned, expired, from another issuer, revoked, or unverifiable */
    public Claims validate(String token) {
        Jws<Claims> jws;
        try {
            jws = Jwts.parser()
                    .keyLocator(new LocatorAdapter<Key>() {
                        @Override
                        protected Key locate(JwsHeader header) {
                            return resolveKey(header.getKeyId());
                        }
                    })
                    .requireIssuer(issuer)
                    .clockSkewSeconds(30)
                    .clock(() -> java.util.Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token);
        } catch (IllegalArgumentException e) {
            throw new JwtException("Malformed token", e);
        }
        if (!"RS256".equals(jws.getHeader().getAlgorithm())) {
            throw new JwtException("Unsupported signing algorithm");
        }
        Claims claims = jws.getPayload();
        String jti = claims.getId();
        if (jti == null || jti.isBlank()) {
            throw new JwtException("Token has no jti");
        }
        if (isBlocklisted(jti)) {
            throw new JwtException("Token has been revoked");
        }
        return claims;
    }

    public String jtiOf(Claims claims) {
        return claims.getId();
    }

    // ------------------------------------------------------------------ blocklist

    private boolean isBlocklisted(String jti) {
        if (redis == null) {
            if (warnedNoRedis.compareAndSet(false, true)) {
                log.warn("No RedisTemplate<String,String> bean: the jwt_blocklist check is SKIPPED, logged-out tokens stay valid until they expire");
            }
            return false;
        }
        try {
            return Boolean.TRUE.equals(redis.hasKey(BLOCKLIST_PREFIX + jti));
        } catch (RuntimeException e) {
            if (blocklistFailOpen) {
                log.warn("Blocklist lookup failed, accepting the token (jwt.blocklist-fail-open=true): {}", e.getMessage());
                return false;
            }
            throw new JwtException("Cannot verify token revocation status", e);
        }
    }

    // ------------------------------------------------------------------ keys

    private Key resolveKey(String kid) {
        if (staticKey != null) {
            return staticKey;
        }
        if (authUrl.isEmpty()) {
            throw new JwtException("No verification key configured");
        }
        Instant now = clock.instant();
        boolean stale = Duration.between(jwksFetchedAt, now).compareTo(JWKS_TTL) >= 0;
        boolean unknownKid = kid != null && !jwks.containsKey(kid);
        boolean mayRetry = Duration.between(jwksAttemptedAt, now).compareTo(JWKS_MIN_REFETCH) >= 0;
        if (jwks.isEmpty() || stale || (unknownKid && mayRetry)) {
            refreshJwks(now);
        }
        Map<String, PublicKey> keys = jwks;
        if (kid != null && keys.containsKey(kid)) {
            return keys.get(kid);
        }
        if (kid == null && keys.size() == 1) {
            return keys.values().iterator().next();
        }
        throw new JwtException("Unknown signing key");
    }

    private synchronized void refreshJwks(Instant now) {
        jwksAttemptedAt = now;
        try {
            HttpResponse<String> response = http.send(
                    HttpRequest.newBuilder(URI.create(authUrl + "/api/auth/.well-known/jwks.json"))
                            .timeout(Duration.ofSeconds(10)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("JWKS endpoint answered " + response.statusCode());
            }
            Map<String, PublicKey> fresh = new HashMap<>();
            for (JsonNode key : mapper.readTree(response.body()).path("keys")) {
                if (!"RSA".equals(key.path("kty").asText())) {
                    continue;
                }
                RSAPublicKeySpec spec = new RSAPublicKeySpec(
                        new BigInteger(1, Base64.getUrlDecoder().decode(key.path("n").asText())),
                        new BigInteger(1, Base64.getUrlDecoder().decode(key.path("e").asText())));
                fresh.put(key.path("kid").asText(""), KeyFactory.getInstance("RSA").generatePublic(spec));
            }
            if (fresh.isEmpty()) {
                throw new IllegalStateException("JWKS contains no RSA key");
            }
            jwks = Map.copyOf(fresh);
            jwksFetchedAt = now;
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("Could not refresh the JWKS from {}: {}", authUrl, e.getMessage());
            // keep serving the previous keys if there are any; with none, resolveKey rejects the token
        }
    }

    static PublicKey parsePublicKey(String raw) {
        try {
            String text = raw.trim();
            if (!text.contains("-----BEGIN")) {
                byte[] decoded = Base64.getMimeDecoder().decode(text);
                String asText = new String(decoded, StandardCharsets.US_ASCII);
                if (!asText.contains("-----BEGIN")) {
                    return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(decoded)); // base64 DER
                }
                text = asText; // base64 of the PEM file
            }
            byte[] der = Base64.getMimeDecoder().decode(text.replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", ""));
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IllegalStateException("jwt.public-key (JWT_PUBLIC_KEY) is not a valid RSA public key", e);
        }
    }
}
