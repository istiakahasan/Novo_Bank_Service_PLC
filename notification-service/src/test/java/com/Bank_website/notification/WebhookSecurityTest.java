package com.Bank_website.notification;

import com.Bank_website.notification.config.ServiceSecurityConfig;
import com.Bank_website.notification.controller.NotificationWebhookController;
import com.Bank_website.notification.security.QStashSignatureVerifier;
import com.Bank_website.notification.service.NotificationService;
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

@WebMvcTest(NotificationWebhookController.class)
@Import({SecurityConfig.class, ServiceSecurityConfig.class})
class WebhookSecurityTest {

    @Autowired MockMvc mvc;
    @MockBean NotificationService notificationService;
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
