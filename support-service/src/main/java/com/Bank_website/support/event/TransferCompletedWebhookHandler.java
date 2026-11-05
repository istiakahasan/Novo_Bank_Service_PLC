package com.Bank_website.support.event;

import com.Bank_website.support.service.TicketService;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import com.Bank_website.support.repository.SupportTicketRepository;

import java.time.Duration;
import java.util.UUID;

/**
 * Handles a verified transfer.completed delivery. The controller verifies the Upstash signature BEFORE calling this
 * class, so nothing here runs (and no database is touched) for an unsigned request.
 *
 * QStash sends every event on the topic to this endpoint, so anything that is not a flagged transfer is ignored with a 200.
 * Idempotent: a ticket with the same related_transaction_id (including one a customer opened) means 200 and no new ticket.
 */
@Service
public class TransferCompletedWebhookHandler {

    private static final Logger log = LoggerFactory.getLogger(TransferCompletedWebhookHandler.class);
    private static final Duration LOCK_TTL = Duration.ofSeconds(30);

    public record WebhookResult(String status, @JsonProperty("ticket_id") UUID ticketId, String reason) {
        static WebhookResult ignored(String reason) {
            return new WebhookResult("ignored", null, reason);
        }

        static WebhookResult duplicate() {
            return new WebhookResult("duplicate", null, "ticket already exists for this transaction");
        }
    }

    private final ObjectMapper objectMapper;
    private final SupportTicketRepository tickets;
    private final TicketService ticketService;
    private final RestClient customerClient;
    private final StringRedisTemplate redis;

    public TransferCompletedWebhookHandler(ObjectMapper objectMapper,
                                           SupportTicketRepository tickets,
                                           TicketService ticketService,
                                           @Qualifier("customerServiceClient") RestClient customerClient,
                                           StringRedisTemplate redis) {
        this.objectMapper = objectMapper;
        this.tickets = tickets;
        this.ticketService = ticketService;
        this.customerClient = customerClient;
        this.redis = redis;
    }

    public WebhookResult handle(String rawBody) {
        JsonNode payload;
        try {
            payload = objectMapper.readTree(rawBody);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Malformed JSON payload");
        }

        String event = payload.path("event").asText(null);
        if (event != null && !"transfer.completed".equals(event)) {
            return WebhookResult.ignored("not a transfer.completed event");
        }
        if (!payload.path("fraud_flagged").asBoolean(false)) {
            return WebhookResult.ignored("transfer is not fraud flagged");
        }

        UUID transactionId = uuid(payload, "transaction_id");
        UUID fromAccount = uuid(payload, "from_account");

        String lockKey = "support:txn-lock:" + transactionId;
        if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(lockKey, "1", LOCK_TTL))) {
            return WebhookResult.duplicate(); // another delivery of the same event is being processed
        }
        try {
            if (tickets.existsByRelatedTransactionId(transactionId)) {
                return WebhookResult.duplicate();
            }
            UUID customerId = resolveCustomer(fromAccount);
            if (customerId == null) {
                log.warn("No customer for account {}; no ticket created for transaction {}", fromAccount, transactionId);
                return WebhookResult.ignored("no customer found for from_account");
            }
            UUID ticketId = ticketService.createFraudTicket(customerId, transactionId);
            log.info("Fraud ticket {} created for transaction {}", ticketId, transactionId);
            return new WebhookResult("created", ticketId, null);
        } finally {
            redis.delete(lockKey);
        }
    }

    private UUID resolveCustomer(UUID accountId) {
        try {
            JsonNode customer = customerClient.get()
                    .uri("/api/customers/by-account/{id}", accountId)
                    .retrieve()
                    .body(JsonNode.class);
            if (customer == null || customer.path("id").isMissingNode()) {
                return null;
            }
            return UUID.fromString(customer.path("id").asText());
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        }
    }

    private UUID uuid(JsonNode payload, String field) {
        String value = payload.path(field).asText(null);
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return UUID.fromString(value);
    }
}
