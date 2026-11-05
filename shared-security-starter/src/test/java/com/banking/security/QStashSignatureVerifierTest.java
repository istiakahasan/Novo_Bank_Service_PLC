package com.banking.security;

import com.banking.security.util.QStashSignatureVerifier;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QStashSignatureVerifierTest {

    private static final byte[] BODY = "{\"event\":\"transfer.completed\"}".getBytes(StandardCharsets.UTF_8);
    private final QStashSignatureVerifier verifier = new QStashSignatureVerifier("current-key", "next-key", true);
    private final long future = Instant.now().getEpochSecond() + 300;

    @Test
    void v1HmacWithCurrentAndNextKey() throws Exception {
        assertTrue(verifier.verify(BODY, TestKeys.hmacV1("current-key", BODY)));
        assertTrue(verifier.verify(BODY, TestKeys.hmacV1("next-key", BODY)));
    }

    @Test
    void v1RejectsTamperedBodyWrongKeyAndGarbage() throws Exception {
        String signature = TestKeys.hmacV1("current-key", BODY);
        assertFalse(verifier.verify("{\"event\":\"x\"}".getBytes(StandardCharsets.UTF_8), signature));
        assertFalse(verifier.verify(BODY, TestKeys.hmacV1("other-key", BODY)));
        assertFalse(verifier.verify(BODY, "v1=%%%not-base64"));
        assertFalse(verifier.verify(BODY, "v1="));
    }

    @Test
    void realUpstashJwtFormat() throws Exception {
        assertTrue(verifier.verify(BODY, TestKeys.upstashJwt("current-key", BODY, future)));
        assertTrue(verifier.verify(BODY, TestKeys.upstashJwt("next-key", BODY, future)));
    }

    @Test
    void jwtFormatRejectsTamperedBodyWrongKeyExpiredAndMalformed() throws Exception {
        String valid = TestKeys.upstashJwt("current-key", BODY, future);
        assertFalse(verifier.verify("{\"event\":\"x\"}".getBytes(StandardCharsets.UTF_8), valid));
        assertFalse(verifier.verify(BODY, TestKeys.upstashJwt("other-key", BODY, future)));
        assertFalse(verifier.verify(BODY, TestKeys.upstashJwt("current-key", BODY, Instant.now().getEpochSecond() - 3600)));
        assertFalse(verifier.verify(BODY, "aaa.bbb.ccc"));
        assertFalse(verifier.verify(BODY, "not a signature"));
    }

    @Test
    void missingInputsAndMissingKeysNeverVerify() throws Exception {
        assertFalse(verifier.verify(BODY, null));
        assertFalse(verifier.verify(BODY, ""));
        assertFalse(verifier.verify(null, TestKeys.hmacV1("current-key", BODY)));
        QStashSignatureVerifier noKeys = new QStashSignatureVerifier("", "", true);
        assertFalse(noKeys.verify(BODY, TestKeys.hmacV1("any-key", BODY)));
        assertFalse(noKeys.verify(BODY, TestKeys.upstashJwt("any-key", BODY, future)));
    }

    @Test
    void v1CanBeSwitchedOff() throws Exception {
        QStashSignatureVerifier strict = new QStashSignatureVerifier("current-key", "", false);
        assertFalse(strict.verify(BODY, TestKeys.hmacV1("current-key", BODY)));
        assertTrue(strict.verify(BODY, TestKeys.upstashJwt("current-key", BODY, future)));
    }
}
