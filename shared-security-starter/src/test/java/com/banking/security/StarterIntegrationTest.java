package com.banking.security;

import com.banking.security.testapp.TestApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A real Spring context of a service that only has the starter on its classpath and no security code. */
@SpringBootTest(classes = TestApplication.class, properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"})
@AutoConfigureMockMvc
class StarterIntegrationTest {

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("jwt.public-key", () -> TestKeys.publicKeyBase64Der(TestKeys.PAIR));
        registry.add("qstash.current-signing-key", () -> "current-key");
        registry.add("qstash.next-signing-key", () -> "next-key");
        registry.add("cors.allowed-origins", () -> "https://portal.example.com");
    }

    @Autowired MockMvc mvc;

    @BeforeEach
    void reset() {
        TestApplication.WEBHOOK_CALLS.set(0);
    }

    private static final String BODY = "{\"event\":\"transfer.completed\",\"fraud_flagged\":true}";

    // ---------------------------------------------------------------- JWT

    @Test
    void noTokenOnAProtectedPathIs401Json() throws Exception {
        mvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Authentication required"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void validTokenReachesTheControllerWithRoleAndCustomerId() throws Exception {
        UUID cid = UUID.randomUUID();
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + TestKeys.token("CUSTOMER", cid)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.customerId").value(cid.toString()));
    }

    @Test
    void invalidExpiredForeignAndTamperedTokensAre401WithTheTokenMessage() throws Exception {
        String[] bad = {
                "garbage",
                TestKeys.token(TestKeys.PAIR, "banking-auth", "STAFF", null, -3600, "j", "v1"),
                TestKeys.token(TestKeys.PAIR, "other-issuer", "STAFF", null, 900, "j", "v1"),
                TestKeys.token(TestKeys.OTHER, "banking-auth", "STAFF", null, 900, "j", "v1"),
        };
        for (String token : bad) {
            mvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("Invalid or expired token"));
        }
        String[] parts = TestKeys.token("CUSTOMER", UUID.randomUUID()).split("\\.");
        String forged = Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("{\"iss\":\"banking-auth\",\"sub\":\"" + UUID.randomUUID() + "\",\"role\":\"STAFF\",\"jti\":\"j\",\"exp\":9999999999}")
                        .getBytes(StandardCharsets.UTF_8));
        mvc.perform(get("/api/staff-only").header("Authorization", "Bearer " + parts[0] + "." + forged + "." + parts[2]))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongRoleIs403JsonAndTheRightRoleIs200() throws Exception {
        mvc.perform(get("/api/staff-only").header("Authorization", "Bearer " + TestKeys.token("CUSTOMER", UUID.randomUUID())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Access denied"));
        mvc.perform(get("/api/staff-only").header("Authorization", "Bearer " + TestKeys.token("STAFF", null)))
                .andExpect(status().isOk());
        // an unknown role claim yields no authority at all
        mvc.perform(get("/api/staff-only").header("Authorization", "Bearer " + TestKeys.token("ADMIN", null)))
                .andExpect(status().isForbidden());
    }

    @Test
    void healthIsPublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    // ---------------------------------------------------------------- QStash webhooks

    @Test
    void webhookWithAValidV1SignaturePassesAndTheControllerStillReadsTheBody() throws Exception {
        mvc.perform(post("/webhooks/echo").contentType(MediaType.APPLICATION_JSON).content(BODY)
                        .header("Upstash-Signature", TestKeys.hmacV1("current-key", BODY.getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.received").value(BODY));
        assertEquals(1, TestApplication.WEBHOOK_CALLS.get());
    }

    @Test
    void webhookAcceptsTheRealUpstashJwtAndTheNextKeyAndTheQStashSignatureAlias() throws Exception {
        long exp = Instant.now().getEpochSecond() + 300;
        mvc.perform(post("/webhooks/echo").contentType(MediaType.APPLICATION_JSON).content(BODY)
                        .header("Upstash-Signature", TestKeys.upstashJwt("next-key", BODY.getBytes(StandardCharsets.UTF_8), exp)))
                .andExpect(status().isOk());
        mvc.perform(post("/webhooks/echo").contentType(MediaType.APPLICATION_JSON).content(BODY)
                        .header("QStash-Signature", TestKeys.hmacV1("next-key", BODY.getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isOk());
        assertEquals(2, TestApplication.WEBHOOK_CALLS.get());
    }

    @Test
    void tamperedBodyMissingAndWrongSignaturesAre401BeforeTheController() throws Exception {
        String signature = TestKeys.hmacV1("current-key", BODY.getBytes(StandardCharsets.UTF_8));
        mvc.perform(post("/webhooks/echo").contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.replace("true", "false")).header("Upstash-Signature", signature))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid or missing signature"));
        mvc.perform(post("/webhooks/echo").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/webhooks/echo").contentType(MediaType.APPLICATION_JSON).content(BODY)
                        .header("Upstash-Signature", TestKeys.hmacV1("wrong-key", BODY.getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isUnauthorized());
        // a perfectly good user JWT does not replace the signature
        mvc.perform(post("/webhooks/echo").contentType(MediaType.APPLICATION_JSON).content(BODY)
                        .header("Authorization", "Bearer " + TestKeys.token("STAFF", null)))
                .andExpect(status().isUnauthorized());
        assertEquals(0, TestApplication.WEBHOOK_CALLS.get());
    }

    // ---------------------------------------------------------------- CORS

    @Test
    void corsAllowsOnlyConfiguredOrigins() throws Exception {
        mvc.perform(options("/api/me").header("Origin", "https://portal.example.com")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://portal.example.com"));
        mvc.perform(options("/api/me").header("Origin", "https://evil.example.com")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }
}
