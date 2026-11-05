package com.Bank_website.support;

import com.Bank_website.support.config.SecurityConfig;
import com.Bank_website.support.controller.SupportWebhookController;
import com.Bank_website.support.controller.TicketController;
import com.Bank_website.support.dto.TicketListResponse;
import com.Bank_website.support.event.TransferCompletedWebhookHandler;
import com.Bank_website.support.exception.GlobalExceptionHandler;
import com.Bank_website.support.security.QStashSignatureVerifier;
import com.Bank_website.support.service.TicketService;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {TicketController.class, SupportWebhookController.class})
@Import({com.Bank_website.security.SecurityConfig.class, SecurityConfig.class, GlobalExceptionHandler.class})
class SupportSecurityTest {

    private static final KeyPair KEYS = newKeys();

    private static KeyPair newKeys() {
        try {
            KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
            g.initialize(2048);
            return g.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("security.jwt.public-key", () -> Base64.getEncoder().encodeToString(KEYS.getPublic().getEncoded()));
        registry.add("security.system.keys", () -> "fraud-service=fraud-key");
    }

    @Autowired MockMvc mvc;
    @MockBean TicketService ticketService;
    @MockBean TransferCompletedWebhookHandler webhookHandler;
    @MockBean QStashSignatureVerifier verifier;

    private String token(String role, UUID customerId) {
        var b = Jwts.builder().issuer("novo-auth").subject(UUID.randomUUID().toString()).id(UUID.randomUUID().toString())
                .claim("roles", List.of(role)).issuedAt(new Date())
                .expiration(Date.from(Instant.now().plusSeconds(300)));
        if (customerId != null) {
            b.claim("cid", customerId.toString());
        }
        return "Bearer " + b.signWith(KEYS.getPrivate(), Jwts.SIG.RS256).compact();
    }

    private static final String VALID_TICKET = "{\"category\":\"GENERAL_INQUIRY\",\"subject\":\"Help\"}";

    @Test
    void anonymousIsUnauthorizedOnTheApi() throws Exception {
        mvc.perform(post("/api/tickets").contentType(MediaType.APPLICATION_JSON).content(VALID_TICKET))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/tickets")).andExpect(status().isUnauthorized());
    }

    @Test
    void onlyCustomersCreateTickets() throws Exception {
        mvc.perform(post("/api/tickets").header("Authorization", token("STAFF", null))
                .contentType(MediaType.APPLICATION_JSON).content(VALID_TICKET)).andExpect(status().isForbidden());
        mvc.perform(post("/api/tickets").header("Authorization", token("CUSTOMER", UUID.randomUUID()))
                .contentType(MediaType.APPLICATION_JSON).content(VALID_TICKET)).andExpect(status().isCreated());
    }

    @Test
    void onlyStaffPatchTickets() throws Exception {
        UUID id = UUID.randomUUID();
        String body = "{\"status\":\"IN_PROGRESS\"}";
        mvc.perform(patch("/api/tickets/" + id).header("Authorization", token("CUSTOMER", UUID.randomUUID()))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(patch("/api/tickets/" + id).header("Authorization", token("STAFF", null))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
    }

    @Test
    void emptyPatchAndBadEnumsAreBadRequests() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(patch("/api/tickets/" + id).header("Authorization", token("STAFF", null))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/tickets").header("Authorization", token("CUSTOMER", UUID.randomUUID()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"category\":\"NOPE\",\"subject\":\"x\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listIsOpenToCustomersAndStaffButNotToSystem() throws Exception {
        when(ticketService.listTickets(any(), any(), any(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(new TicketListResponse(List.of(), 0, 20, 0, 0));
        mvc.perform(get("/api/tickets").header("Authorization", token("CUSTOMER", UUID.randomUUID()))).andExpect(status().isOk());
        mvc.perform(get("/api/tickets").header("Authorization", token("STAFF", null))).andExpect(status().isOk());
        mvc.perform(get("/api/tickets").header("X-Service-Id", "fraud-service").header("X-Service-Key", "fraud-key"))
                .andExpect(status().isForbidden());
    }

    @Test
    void webhookWithAnInvalidSignatureIs401AndNothingElseRuns() throws Exception {
        when(verifier.verify(any(), any())).thenReturn(false);
        mvc.perform(post("/webhooks/transfer-completed").header("Upstash-Signature", "aaa.bbb.ccc")
                .contentType(MediaType.APPLICATION_JSON).content("{\"fraud_flagged\":true}")).andExpect(status().isUnauthorized());
        mvc.perform(post("/webhooks/transfer-completed").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(webhookHandler, ticketService);
    }

    @Test
    void webhookWithAValidSignatureReachesTheHandlerWithoutAJwt() throws Exception {
        when(verifier.verify(any(), any())).thenReturn(true);
        when(webhookHandler.handle(any())).thenReturn(new TransferCompletedWebhookHandler.WebhookResult("created", UUID.randomUUID(), null));
        mvc.perform(post("/webhooks/transfer-completed").header("Upstash-Signature", "valid")
                .contentType(MediaType.APPLICATION_JSON).content("{\"fraud_flagged\":true}")).andExpect(status().isOk());
        verify(webhookHandler).handle(any());
    }

    @Test
    void aUserJwtDoesNotBypassTheSignatureCheck() throws Exception {
        when(verifier.verify(any(), any())).thenReturn(false);
        mvc.perform(post("/webhooks/transfer-completed").header("Authorization", token("STAFF", null))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
        verify(webhookHandler, never()).handle(any());
    }
}
