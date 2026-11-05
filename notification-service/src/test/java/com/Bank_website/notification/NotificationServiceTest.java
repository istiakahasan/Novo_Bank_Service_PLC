package com.Bank_website.notification;

import com.Bank_website.notification.controller.NotificationWebhookController;
import com.Bank_website.notification.controller.WebhookExceptionHandler;
import com.Bank_website.notification.dto.CustomerCreatedEvent;
import com.Bank_website.notification.dto.CustomerInfo;
import com.Bank_website.notification.dto.NotificationResult;
import com.Bank_website.notification.dto.TransferCompletedEvent;
import com.Bank_website.notification.security.QStashSignatureVerifier;
import com.Bank_website.notification.service.CustomerLookupService;
import com.Bank_website.notification.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
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

@SuppressWarnings("unchecked")
class NotificationServiceTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String KEY = "current-key";

    private final JavaMailSender mail = mock(JavaMailSender.class);
    private final CustomerLookupService lookup = mock(CustomerLookupService.class);
    private final RedisTemplate<String, String> redis = mock(RedisTemplate.class);
    private final ValueOperations<String, String> ops = mock(ValueOperations.class);
    private final NotificationService service;

    NotificationServiceTest() {
        when(redis.opsForValue()).thenReturn(ops);
        service = new NotificationService(mail, lookup, redis, "noreply@novobank.test");
    }

    private TransferCompletedEvent event() {
        return new TransferCompletedEvent("transfer.completed", UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), 12_345L, "t");
    }

    @Test
    void sendsTransferEmailWithFormattedAmount() {
        TransferCompletedEvent e = event();
        when(lookup.findByAccount(e.fromAccount())).thenReturn(Optional.of(
                new CustomerInfo(UUID.randomUUID(), "CIF-000001", "Ada", "01712345678", "ada@example.com")));

        NotificationResult r = service.notifyTransferCompleted(e);

        ArgumentCaptor<SimpleMailMessage> msg = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail).send(msg.capture());
        assertEquals("ada@example.com", msg.getValue().getTo()[0]);
        assertTrue(msg.getValue().getText().contains("You sent $123.45 to account " + e.toAccount()));
        assertTrue(r.emailSent());
        assertTrue(r.smsLogged());
        verify(ops).set(any(String.class), any(String.class), any(java.time.Duration.class));
    }

    @Test
    void duplicateDeliveryDoesNotResend() {
        TransferCompletedEvent e = event();
        when(redis.hasKey("notif_done:" + e.transactionId())).thenReturn(true);
        NotificationResult r = service.notifyTransferCompleted(e);
        assertTrue(r.duplicate());
        verify(mail, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void customerWithoutEmailSkipsEmailButStillSucceeds() {
        TransferCompletedEvent e = event();
        when(lookup.findByAccount(e.fromAccount())).thenReturn(Optional.of(
                new CustomerInfo(UUID.randomUUID(), "CIF-000001", "Ada", "01712345678", null)));
        NotificationResult r = service.notifyTransferCompleted(e);
        assertFalse(r.emailSent());
        verify(mail, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void welcomeEmailGoesToCustomerEmail() {
        CustomerCreatedEvent e = new CustomerCreatedEvent("customer.created", UUID.randomUUID(), "CIF-000002",
                "Bob", "bob@example.com", "01712345679");
        assertTrue(service.notifyCustomerCreated(e).emailSent());
        ArgumentCaptor<SimpleMailMessage> msg = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail).send(msg.capture());
        assertEquals("Welcome to Novo Bank", msg.getValue().getSubject());
    }

    // ---------- signature + controller ----------

    private static String b64(byte[] b) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    static String sign(String key, String body, long exp) throws Exception {
        String header = b64("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String bodyHash = b64(MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8)));
        long now = Instant.now().getEpochSecond();
        String claims = b64(("{\"iss\":\"Upstash\",\"exp\":" + exp + ",\"nbf\":" + now + ",\"body\":\""
                + bodyHash + "\"}").getBytes(StandardCharsets.UTF_8));
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return header + "." + claims + "." + b64(mac.doFinal((header + "." + claims).getBytes(StandardCharsets.UTF_8)));
    }

    private final QStashSignatureVerifier verifier = new QStashSignatureVerifier(true, KEY, "next-key", JSON);

    @Test
    void verifierChecksSignatureBodyAndExpiry() throws Exception {
        long exp = Instant.now().getEpochSecond() + 300;
        assertTrue(verifier.verify(sign(KEY, "{}", exp), "{}"));
        assertTrue(verifier.verify(sign("next-key", "{}", exp), "{}"));
        assertFalse(verifier.verify(sign(KEY, "{}", exp), "{\"x\":1}"));
        assertFalse(verifier.verify(sign("bad", "{}", exp), "{}"));
        assertFalse(verifier.verify(sign(KEY, "{}", Instant.now().getEpochSecond() - 3600), "{}"));
        assertFalse(verifier.verify(null, "{}"));
    }

    private MockMvc mvc(QStashSignatureVerifier v) {
        return MockMvcBuilders.standaloneSetup(new NotificationWebhookController(service, v, JSON,
                Validation.buildDefaultValidatorFactory().getValidator()))
                .setControllerAdvice(new WebhookExceptionHandler()).build();
    }

    @Test
    void controllerRejectsUnsignedAndIgnoresOtherEvents() throws Exception {
        mvc(verifier).perform(post("/webhooks/customer-created").contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isUnauthorized());

        QStashSignatureVerifier off = new QStashSignatureVerifier(false, "", "", JSON);
        mvc(off).perform(post("/webhooks/customer-created").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"event\":\"transfer.completed\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ignored").value(true));
    }

    @Test
    void controllerSendsWelcomeForSignedCustomerCreated() throws Exception {
        String body = "{\"event\":\"customer.created\",\"customer_id\":\"" + UUID.randomUUID()
                + "\",\"customer_number\":\"CIF-000009\",\"display_name\":\"Eve\",\"email\":\"eve@example.com\"}";
        mvc(verifier).perform(post("/webhooks/customer-created")
                        .header("Upstash-Signature", sign(KEY, body, Instant.now().getEpochSecond() + 300))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email_sent").value(true));
    }
}
