package com.Bank_website.authserver.service;

import com.Bank_website.authserver.entity.AuthUser;
import com.Bank_website.authserver.exception.UnauthorizedException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Issues and validates RS256 access tokens. The private key never leaves this service. */
@Component
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);
    public static final String KEY_ID = "v1";

    public record IssuedToken(String token, String jti, Instant expiresAt) {
    }

    private final RSAPrivateKey privateKey;
    private final RSAPublicKey publicKey;
    private final String issuer;
    private final long accessTtlSeconds;
    private final RedisBlocklistService blocklist;

    public JwtService(@Value("${jwt.private-key}") String privateKeyConfig,
                      @Value("${jwt.public-key:}") String publicKeyConfig,
                      @Value("${jwt.issuer}") String issuer,
                      @Value("${jwt.access-ttl-seconds}") long accessTtlSeconds,
                      RedisBlocklistService blocklist) {
        this.privateKey = RsaKeyLoader.privateKey(privateKeyConfig);
        RSAPublicKey derived = RsaKeyLoader.derivePublic(privateKey);
        if (publicKeyConfig != null && !publicKeyConfig.isBlank()) {
            RSAPublicKey configured = RsaKeyLoader.publicKey(publicKeyConfig);
            if (!configured.getModulus().equals(derived.getModulus())) {
                // services verify with JWT_PUBLIC_KEY: a mismatch would make every token they receive invalid
                throw new IllegalStateException("JWT_PUBLIC_KEY does not belong to JWT_PRIVATE_KEY");
            }
            this.publicKey = configured;
        } else {
            log.warn("JWT_PUBLIC_KEY is not set; using the public key derived from JWT_PRIVATE_KEY");
            this.publicKey = derived;
        }
        this.issuer = issuer;
        this.accessTtlSeconds = accessTtlSeconds;
        this.blocklist = blocklist;
    }

    /**
     * Claims: sub=userId, role, customerId (CUSTOMER) or agentId (STAFF), iat, exp=iat+900, jti, iss.
     * "roles" and "cid" duplicate role / customerId because the shared security-starter reads those names.
     */
    public IssuedToken generateAccessToken(AuthUser user) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plusSeconds(accessTtlSeconds);
        String jti = UUID.randomUUID().toString();
        var builder = Jwts.builder()
                .header().keyId(KEY_ID).and()
                .issuer(issuer)
                .subject(user.getUserId().toString())
                .id(jti)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .claim("role", user.getRole())
                .claim("roles", List.of(user.getRole()));
        if (user.getCustomerId() != null) {
            builder.claim("customerId", user.getCustomerId().toString());
            builder.claim("cid", user.getCustomerId().toString());
        }
        if (user.getAgentId() != null) {
            builder.claim("agentId", user.getAgentId().toString());
        }
        return new IssuedToken(builder.signWith(privateKey, Jwts.SIG.RS256).compact(), jti, expiresAt);
    }

    /** Signature, issuer, expiry and blocklist. @throws UnauthorizedException for any failure. */
    public Claims validateToken(String token) {
        Claims claims;
        try {
            claims = Jwts.parser().verifyWith(publicKey).requireIssuer(issuer).clockSkewSeconds(30)
                    .build().parseSignedClaims(token).getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            throw new UnauthorizedException("Invalid or expired token");
        }
        if (blocklist.isBlocklisted(claims.getId())) {
            throw new UnauthorizedException("Token has been revoked");
        }
        return claims;
    }

    public String getJti(Claims claims) {
        return claims.getId();
    }

    /** Blocklists a token id until the token would have expired anyway. */
    public void addToBlocklist(String jti, long ttlSeconds) {
        blocklist.blocklist(jti, ttlSeconds);
    }

    public long accessTtlSeconds() {
        return accessTtlSeconds;
    }

    /** RFC 7517 JWK Set with the single signing key. */
    public Map<String, Object> jwks() {
        Map<String, Object> jwk = new LinkedHashMap<>();
        jwk.put("kty", "RSA");
        jwk.put("kid", KEY_ID);
        jwk.put("use", "sig");
        jwk.put("alg", "RS256");
        jwk.put("n", base64Url(publicKey.getModulus()));
        jwk.put("e", base64Url(publicKey.getPublicExponent()));
        return Map.of("keys", List.of(jwk));
    }

    private static String base64Url(BigInteger value) {
        byte[] bytes = value.toByteArray();
        int start = bytes.length > 1 && bytes[0] == 0 ? 1 : 0; // drop the sign byte
        byte[] unsigned = java.util.Arrays.copyOfRange(bytes, start, bytes.length);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(unsigned);
    }
}
