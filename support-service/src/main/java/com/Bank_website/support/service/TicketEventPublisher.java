package com.Bank_website.support.service;

import com.Bank_website.support.entity.SupportTicket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Publishes ticket.created and ticket.status_changed to the QStash topic. Best effort: a QStash outage is
 * logged and never rolls back a ticket. Payloads carry ids only (no subject or message text).
 */
@Service
public class TicketEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(TicketEventPublisher.class);

    private final RestClient restClient;
    private final String topicUrl;
    private final String token;

    public TicketEventPublisher(RestClient.Builder builder,
                                @Value("${qstash.topic-url}") String topicUrl,
                                @Value("${qstash.token}") String token) {
        this.restClient = builder.build();
        this.topicUrl = topicUrl;
        this.token = token;
    }

    public void publishTicketCreated(SupportTicket ticket) {
        Map<String, Object> payload = base("ticket.created", ticket);
        payload.put("category", ticket.getCategory());
        payload.put("priority", ticket.getPriority());
        payload.put("status", ticket.getStatus());
        payload.put("related_transaction_id", ticket.getRelatedTransactionId());
        send(payload);
    }

    public void publishStatusChanged(SupportTicket ticket, String fromStatus) {
        Map<String, Object> payload = base("ticket.status_changed", ticket);
        payload.put("from_status", fromStatus);
        payload.put("to_status", ticket.getStatus());
        send(payload);
    }

    private Map<String, Object> base(String event, SupportTicket ticket) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", event);
        payload.put("ticket_id", ticket.getTicketId());
        payload.put("customer_id", ticket.getCustomerId());
        payload.put("occurred_at", OffsetDateTime.now().toString());
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
            log.warn("Failed to publish {} to QStash: {}", payload.get("event"), ex.getMessage());
        }
    }
}
