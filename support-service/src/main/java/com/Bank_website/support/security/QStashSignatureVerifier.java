package com.Bank_website.support.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

/**
 * Verifies the {@code Upstash-Signature} header sent by QStash.
 *
 * The header is NOT a bare HMAC of the body: it is a JWT (HS256) signed with your signing key.
 * Its claims include iss="Upstash", exp/nbf, and "body" = base64url(SHA-256(raw request body)).
 * We therefore check (1) HMAC-SHA256 of "header.payload" with the current OR next signing key
 * (QStash rotates keys), (2) issuer and time window, (3) the body hash matches the received body.
 */
@Component
public class QStashSignatureVerifier {

    private static final Logger log = LoggerFactory.getLogger(QStashSignatureVerifier.class);
    private static final long CLOCK_SKEW_SECONDS = 60;

    private final boolean enabled;
    private final List<String> keys;
    private final ObjectMapper objectMapper;

    public QStashSignatureVerifier(@Value("${qstash.verify-signatures:true}") boolean enabled,
                                   @Value("${qstash.current-signing-key:}") String currentKey,
                                   @Value("${qstash.next-signing-key:}") String nextKey,
                                   ObjectMapper objectMapper) {
        this.enabled = enabled;
        this.keys = List.of(currentKey, nextKey).stream().filter(k -> k != null && !k.isBlank()).toList();
        this.objectMapper = objectMapper;
        if (!enabled) {
            log.warn("QStash signature verification is DISABLED - use only for local testing");
        }
    }

    public boolean verify(String signatureHeader, String rawBody) {
        if (!enabled) {
            return true;
        }
        if (signatureHeader == null || rawBody == null || keys.isEmpty()) {
            return false;
        }
        String[] parts = signatureHeader.split("\\.");
        if (parts.length != 3) {
            return false;
        }
        try {
            for (String key : keys) {
                if (signatureMatches(parts, key) && claimsValid(parts[0], parts[1], rawBody)) {
                    return true;
                }
            }
        } catch (Exception e) {
            log.debug("Signature verification error: {}", e.getMessage());
        }
        return false;
    }

    private boolean signatureMatches(String[] parts, String key) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] expected = mac.doFinal((parts[0] + "." + parts[1]).getBytes(StandardCharsets.UTF_8));
        byte[] actual = Base64.getUrlDecoder().decode(parts[2]);
        return MessageDigest.isEqual(expected, actual); // constant-time compare
    }

    private boolean claimsValid(String headerPart, String payloadPart, String rawBody) throws Exception {
        JsonNode header = objectMapper.readTree(Base64.getUrlDecoder().decode(headerPart));
        if (!"HS256".equals(header.path("alg").asText())) {
            return false;
        }
        JsonNode claims = objectMapper.readTree(Base64.getUrlDecoder().decode(payloadPart));
        if (!"Upstash".equals(claims.path("iss").asText())) {
            return false;
        }
        long now = Instant.now().getEpochSecond();
        if (claims.has("exp") && now > claims.get("exp").asLong() + CLOCK_SKEW_SECONDS) {
            return false;
        }
        if (claims.has("nbf") && now < claims.get("nbf").asLong() - CLOCK_SKEW_SECONDS) {
            return false;
        }
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(rawBody.getBytes(StandardCharsets.UTF_8));
        String expectedBody = Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        String claimedBody = claims.path("body").asText("").replace("=", "");
        return MessageDigest.isEqual(expectedBody.getBytes(StandardCharsets.UTF_8),
                claimedBody.getBytes(StandardCharsets.UTF_8));
    }
}
