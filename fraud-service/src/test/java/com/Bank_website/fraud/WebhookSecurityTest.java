package com.Bank_website.fraud;

import com.Bank_website.fraud.config.ServiceSecurityConfig;
import com.Bank_website.fraud.controller.FraudWebhookController;
import com.Bank_website.fraud.security.QStashSignatureVerifier;
import com.Bank_website.fraud.service.FraudService;
import com.Bank_website.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FraudWebhookController.class)
@Import({SecurityConfig.class, ServiceSecurityConfig.class})
class WebhookSecurityTest {

    @Autowired MockMvc mvc;
    @MockBean FraudService fraudService;
    @MockBean QStashSignatureVerifier verifier;

    @Test
    void webhookWithoutValidSignatureIs401() throws Exception {
        mvc.perform(post("/webhooks/transfer-completed").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void userJwtDoesNotOpenTheWebhook() throws Exception {
        mvc.perform(post("/webhooks/customer-created").header("Authorization", "Bearer not-a-real-token")
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
    }

    @Test
    void everythingElseIsDenied() throws Exception {
        mvc.perform(get("/api/anything")).andExpect(status().isUnauthorized());
        mvc.perform(post("/admin").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }
}
