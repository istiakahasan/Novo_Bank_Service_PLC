package com.Bank_website.cards.security;

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
            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
            JsonNode payload = objectMapper.readTree(payloadBytes);

            if (!"Upstash".equals(payload.path("iss").asText())) {
                return false;
            }

            long now = Instant.now().getEpochSecond();
            long exp = payload.path("exp").asLong(0);
            long nbf = payload.path("nbf").asLong(0);
            if (now > exp + CLOCK_SKEW_SECONDS || now < nbf - CLOCK_SKEW_SECONDS) {
                return false;
            }

            byte[] bodyHash = MessageDigest.getInstance("SHA-256").digest(rawBody.getBytes(StandardCharsets.UTF_8));
            String expectedBodyHash = Base64.getUrlEncoder().withoutPadding().encodeToString(bodyHash);
            String tokenBodyHash = payload.path("body").asText();
            if (!MessageDigest.isEqual(expectedBodyHash.getBytes(StandardCharsets.UTF_8),
                    tokenBodyHash.getBytes(StandardCharsets.UTF_8))) {
                return false;
            }

            byte[] content = (parts[0] + "." + parts[1]).getBytes(StandardCharsets.UTF_8);
            byte[] signature = Base64.getUrlDecoder().decode(parts[2]);
            for (String key : keys) {
                if (matchesHmac(key, content, signature)) {
                    return true;
                }
            }
            return false;
        } catch (Exception ex) {
            log.warn("Signature verification failed: {}", ex.getMessage());
            return false;
        }
    }

    private boolean matchesHmac(String key, byte[] content, byte[] expectedSignature) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] computed = mac.doFinal(content);
        return MessageDigest.isEqual(computed, expectedSignature);
    }
}
