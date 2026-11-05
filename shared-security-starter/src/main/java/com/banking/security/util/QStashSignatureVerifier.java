package com.banking.security.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

/**
 * Verifies QStash webhook deliveries with QSTASH_CURRENT_SIGNING_KEY, then QSTASH_NEXT_SIGNING_KEY.
 *
 * Two header formats are accepted:
 *  1. The real Upstash-Signature header: a JWT (HS256) whose "body" claim is base64url(SHA-256(raw body)),
 *     iss = "Upstash", with exp / nbf. This is what QStash actually sends.
 *  2. "v1=&lt;base64&gt;": base64 of HMAC-SHA256(raw body, key). Not sent by QStash; kept for simple callers and tests.
 *     Disable with qstash.accept-hmac-v1=false.
 * All comparisons are constant time. With no key configured, nothing verifies.
 */
@Component
public class QStashSignatureVerifier {

    private static final Logger log = LoggerFactory.getLogger(QStashSignatureVerifier.class);
    private static final long SKEW_SECONDS = 60;

    private final List<String> keys;
    private final boolean acceptHmacV1;
    private final ObjectMapper mapper = new ObjectMapper();

    public QStashSignatureVerifier(@Value("${qstash.current-signing-key:${QSTASH_CURRENT_SIGNING_KEY:}}") String currentKey,
                                   @Value("${qstash.next-signing-key:${QSTASH_NEXT_SIGNING_KEY:}}") String nextKey,
                                   @Value("${qstash.accept-hmac-v1:true}") boolean acceptHmacV1) {
        this.keys = java.util.stream.Stream.of(currentKey, nextKey).filter(k -> k != null && !k.isBlank()).toList();
        this.acceptHmacV1 = acceptHmacV1;
        if (keys.isEmpty()) {
            log.warn("No QStash signing key configured: every webhook request will be rejected");
        }
    }

    public boolean verify(byte[] rawBody, String upstashSignatureHeader) {
        if (rawBody == null || upstashSignatureHeader == null || upstashSignatureHeader.isBlank() || keys.isEmpty()) {
            return false;
        }
        String header = upstashSignatureHeader.trim();
        boolean ok = false;
        for (String key : keys) { // no early exit: both keys are always evaluated
            ok |= header.startsWith("v1=") ? acceptHmacV1 && matchesHmacV1(rawBody, header.substring(3), key)
                    : matchesJwt(rawBody, header, key);
        }
        return ok;
    }

    private boolean matchesHmacV1(byte[] body, String signature, String key) {
        try {
            byte[] expected = hmac(key, body);
            byte[] provided = decodeBase64(signature);
            return provided != null && MessageDigest.isEqual(expected, provided);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean matchesJwt(byte[] body, String token, String key) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return false;
            }
            byte[] expectedSignature = hmac(key, (parts[0] + "." + parts[1]).getBytes(StandardCharsets.UTF_8));
            byte[] providedSignature = Base64.getUrlDecoder().decode(parts[2]);
            if (!MessageDigest.isEqual(expectedSignature, providedSignature)) {
                return false;
            }
            JsonNode header = mapper.readTree(Base64.getUrlDecoder().decode(parts[0]));
            JsonNode claims = mapper.readTree(Base64.getUrlDecoder().decode(parts[1]));
            if (!"HS256".equals(header.path("alg").asText()) || !"Upstash".equals(claims.path("iss").asText())) {
                return false;
            }
            long now = Instant.now().getEpochSecond();
            if (claims.has("exp") && now > claims.get("exp").asLong() + SKEW_SECONDS) {
                return false;
            }
            if (claims.has("nbf") && now < claims.get("nbf").asLong() - SKEW_SECONDS) {
                return false;
            }
            String bodyHash = Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(body));
            String claimed = claims.path("body").asText("").replace("=", "");
            return MessageDigest.isEqual(bodyHash.getBytes(StandardCharsets.UTF_8), claimed.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return false;
        }
    }

    private static byte[] hmac(String key, byte[] data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return mac.doFinal(data);
    }

    private static byte[] decodeBase64(String value) {
        try {
            return Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException e) {
            try {
                return Base64.getUrlDecoder().decode(value);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
    }
}
