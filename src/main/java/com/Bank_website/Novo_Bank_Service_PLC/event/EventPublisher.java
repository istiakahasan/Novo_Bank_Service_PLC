package com.Bank_website.Novo_Bank_Service_PLC.event;

import com.Bank_website.Novo_Bank_Service_PLC.entity.Customer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Publishes domain events to an Upstash QStash topic. Failures are logged, not thrown,
 * so a QStash outage never rolls back a customer write.
 */
@Component
public class EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(EventPublisher.class);

    private final RestClient restClient;
    private final String topicUrl;
    private final String token;

    public EventPublisher(RestClient.Builder builder,
                          @Value("${qstash.topic-url}") String topicUrl,
                          @Value("${qstash.token}") String token) {
        this.restClient = builder.build();
        this.topicUrl = topicUrl;
        this.token = token;
    }

    public void publishCustomerCreated(Customer customer) {
        Map<String, Object> payload = basePayload("customer.created", customer);
        payload.put("mobileNo", customer.getMobileNo());
        send(payload);
    }

    public void publishKycVerified(Customer customer, String verifiedBy) {
        Map<String, Object> payload = basePayload("kyc.verified", customer);
        payload.put("verifiedBy", verifiedBy);
        send(payload);
    }

    private Map<String, Object> basePayload(String eventType, Customer customer) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventType", eventType);
        payload.put("customerId", customer.getId());
        payload.put("customerNumber", customer.getCustomerNumber());
        payload.put("kycStatus", customer.getKycStatus());
        payload.put("occurredAt", OffsetDateTime.now().toString());
        return payload;
    }

    private void send(Map<String, Object> payload) {
        try {
            restClient.post()
                    .uri(topicUrl)
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ex) {
            log.warn("Failed to publish {} to QStash: {}", payload.get("eventType"), ex.getMessage());
        }
    }
}
