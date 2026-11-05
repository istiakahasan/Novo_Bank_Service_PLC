package com.Bank_website.fraud;

import com.Bank_website.fraud.controller.FraudWebhookController;
import com.Bank_website.fraud.controller.WebhookExceptionHandler;
import com.Bank_website.fraud.dto.CustomerCreatedEvent;
import com.Bank_website.fraud.dto.FraudCheckResult;
import com.Bank_website.fraud.dto.TransferCompletedEvent;
import com.Bank_website.fraud.entity.FraudAlert;
import com.Bank_website.fraud.repository.FraudAlertRepository;
import com.Bank_website.fraud.security.QStashSignatureVerifier;
import com.Bank_website.fraud.service.FraudRuleEngine;
import com.Bank_website.fraud.service.FraudService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FraudServiceTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String KEY = "current-key";

    // ---------- rule engine + service ----------

    private final FraudRuleEngine engine = mock(FraudRuleEngine.class);
    private final FraudAlertRepository alerts = mock(FraudAlertRepository.class);
    private final FraudService service = new FraudService(engine, alerts, mock(RestClient.class), JSON);

    private TransferCompletedEvent event(long cents) {
        return new TransferCompletedEvent("transfer.completed", UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), cents, "2026-01-01T00:00:00Z");
    }

    @Test
    void highValueAndVelocityBothFlagAndPersist() {
        TransferCompletedEvent e = event(600_000);
        when(engine.checkHighValue(600_000L)).thenReturn(true);
        when(engine.checkVelocity(e.fromAccount())).thenReturn(true);

        FraudCheckResult r = service.checkTransfer(e);

        assertTrue(r.flagged());
        assertEquals(List.of("HIGH_VALUE", "VELOCITY"), r.rules());
        verify(alerts, org.mockito.Mockito.times(2)).save(any(FraudAlert.class));
        verify(engine).recordTransaction(e.fromAccount());
        verify(engine).markProcessed(e.transactionId());
    }

    @Test
    void cleanTransferIsNotFlagged() {
        TransferCompletedEvent e = event(1_000);
        FraudCheckResult r = service.checkTransfer(e);
        assertFalse(r.flagged());
        verify(alerts, never()).save(any());
    }

    @Test
    void replayedTransactionReturnsStoredVerdictWithoutRecounting() {
        TransferCompletedEvent e = event(600_000);
        when(engine.isProcessed(e.transactionId())).thenReturn(true);
        FraudAlert stored = new FraudAlert();
        stored.setRuleFired("HIGH_VALUE");
        when(alerts.findByTransactionId(e.transactionId())).thenReturn(List.of(stored));

        FraudCheckResult r = service.checkTransfer(e);

        assertEquals(List.of("HIGH_VALUE"), r.rules());
        verify(engine, never()).recordTransaction(any());
    }

    @Test
    void highValueBoundaryIsStrictlyGreaterThan500000() {
        FraudRuleEngine real = new FraudRuleEngine(null, null, 500_000, 10, 60);
        assertFalse(real.checkHighValue(500_000L));
        assertTrue(real.checkHighValue(500_001L));
    }

    @Test
    void customerCreatedWithoutRiskFactorsStaysLow() {
        CustomerCreatedEvent e = new CustomerCreatedEvent("customer.created", UUID.randomUUID(), "CIF-1",
                "A B", "a@b.c", "01712345678", null, null);
        assertFalse(service.scoreNewCustomer(e).escalated());
        verify(alerts, never()).save(any());
    }

    // ---------- signature verifier ----------

    private static String b64(byte[] b) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    /** Builds a token exactly the way QStash does: HS256 JWT with a SHA-256 body claim. */
    static String sign(String key, String body, long exp) throws Exception {
        String header = b64("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String bodyHash = b64(MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8)));
        long now = Instant.now().getEpochSecond();
        String claims = b64(("{\"iss\":\"Upstash\",\"sub\":\"https://x\",\"exp\":" + exp + ",\"nbf\":" + now
                + ",\"iat\":" + now + ",\"jti\":\"j\",\"body\":\"" + bodyHash + "\"}").getBytes(StandardCharsets.UTF_8));
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return header + "." + claims + "." + b64(mac.doFinal((header + "." + claims).getBytes(StandardCharsets.UTF_8)));
    }

    private final QStashSignatureVerifier verifier = new QStashSignatureVerifier(true, KEY, "next-key", JSON);

    @Test
    void verifierAcceptsCurrentAndNextKeys() throws Exception {
        long exp = Instant.now().getEpochSecond() + 300;
        assertTrue(verifier.verify(sign(KEY, "{\"a\":1}", exp), "{\"a\":1}"));
        assertTrue(verifier.verify(sign("next-key", "{\"a\":1}", exp), "{\"a\":1}"));
    }

    @Test
    void verifierRejectsTamperedBodyWrongKeyExpiredAndMissing() throws Exception {
        long exp = Instant.now().getEpochSecond() + 300;
        assertFalse(verifier.verify(sign(KEY, "{\"a\":1}", exp), "{\"a\":2}"));
        assertFalse(verifier.verify(sign("other", "{\"a\":1}", exp), "{\"a\":1}"));
        assertFalse(verifier.verify(sign(KEY, "{\"a\":1}", Instant.now().getEpochSecond() - 3600), "{\"a\":1}"));
        assertFalse(verifier.verify(null, "{\"a\":1}"));
        assertFalse(verifier.verify("garbage", "{\"a\":1}"));
    }

    // ---------- controller ----------

    private MockMvc mvc(QStashSignatureVerifier v) {
        FraudWebhookController c = new FraudWebhookController(service, v, JSON,
                Validation.buildDefaultValidatorFactory().getValidator());
        return MockMvcBuilders.standaloneSetup(c).setControllerAdvice(new WebhookExceptionHandler()).build();
    }

    @Test
    void controllerRejectsUnsignedRequests() throws Exception {
        mvc(verifier).perform(post("/webhooks/transfer-completed").contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isUnauthorized());
    }

    @Test
    void controllerIgnoresFannedOutEventsOfOtherTypes() throws Exception {
        QStashSignatureVerifier off = new QStashSignatureVerifier(false, "", "", JSON);
        mvc(off).perform(post("/webhooks/transfer-completed").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"event\":\"customer.created\",\"customer_id\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ignored").value(true));
    }

    @Test
    void controllerReturnsVerdictForValidSignedEvent() throws Exception {
        UUID tx = UUID.randomUUID();
        UUID from = UUID.randomUUID();
        String body = "{\"event\":\"transfer.completed\",\"transaction_id\":\"" + tx + "\",\"from_account\":\"" + from
                + "\",\"to_account\":\"" + UUID.randomUUID() + "\",\"amount_cents\":600000,\"created_at\":\"t\"}";
        when(engine.checkHighValue(600_000L)).thenReturn(true);
        String sig = sign(KEY, body, Instant.now().getEpochSecond() + 300);

        mvc(verifier).perform(post("/webhooks/transfer-completed").header("Upstash-Signature", sig)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transaction_id").value(tx.toString()))
                .andExpect(jsonPath("$.flagged").value(true))
                .andExpect(jsonPath("$.rules[0]").value("HIGH_VALUE"));
    }

    @Test
    void controllerReturns400ForMissingFields() throws Exception {
        QStashSignatureVerifier off = new QStashSignatureVerifier(false, "", "", JSON);
        mvc(off).perform(post("/webhooks/transfer-completed").contentType(MediaType.APPLICATION_JSON)
                .content("{\"event\":\"transfer.completed\"}")).andExpect(status().isBadRequest());
    }
}
