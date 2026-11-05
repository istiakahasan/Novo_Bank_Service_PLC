package com.Bank_website.Novo_Bank_Service_PLC.ledger.event;

import com.Bank_website.Novo_Bank_Service_PLC.ledger.entity.Account;
import com.Bank_website.Novo_Bank_Service_PLC.ledger.entity.LedgerEntry;
import com.Bank_website.Novo_Bank_Service_PLC.ledger.entity.Transaction;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds event payloads and POSTs them to QStash. Unlike the customer publisher this one
 * THROWS on failure: the outbox poller relies on that to keep the row unpublished and retry.
 */
@Component("ledgerEventPublisher")
public class EventPublisher {

    public static final String TRANSFER_COMPLETED = "transfer.completed";
    public static final String ACCOUNT_CREATED = "account.created";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String topicUrl;
    private final String token;

    public EventPublisher(RestClient.Builder builder,
                          ObjectMapper objectMapper,
                          @Value("${qstash.topic-url}") String topicUrl,
                          @Value("${qstash.token}") String token) {
        this.restClient = builder.build();
        this.objectMapper = objectMapper;
        this.topicUrl = topicUrl;
        this.token = token;
    }

    /** JSON stored in outbox.payload. */
    public String transferCompletedPayload(Transaction tx, LedgerEntry debit, LedgerEntry credit) {
        Map<String, Object> payload = new LinkedHashMap<>();
        // Wire contract consumed by fraud-service and notification-service (snake_case).
        payload.put("event", TRANSFER_COMPLETED);
        payload.put("transaction_id", tx.getId());
        payload.put("from_account", tx.getFromAccount());
        payload.put("to_account", tx.getToAccount());
        payload.put("amount_cents", tx.getAmountCents());
        payload.put("created_at", (tx.getCreatedAt() != null ? tx.getCreatedAt() : OffsetDateTime.now()).toString());
        payload.put("idempotency_key", tx.getIdempotencyKey());
        payload.put("status", tx.getStatus());
        payload.put("debit_entry_id", debit.getId());
        payload.put("credit_entry_id", credit.getId());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise outbox payload", e);
        }
    }

    /** JSON stored in outbox.payload for account.created. */
    public String accountCreatedPayload(Account account, long initialDepositCents) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", ACCOUNT_CREATED);
        payload.put("account_id", account.getId());
        payload.put("customer_id", account.getCustomerId());
        payload.put("account_number", account.getAccountNumber());
        payload.put("account_type", account.getAccountType());
        payload.put("initial_deposit_cents", initialDepositCents);
        payload.put("created_at", account.getCreatedAt().toString());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise outbox payload", e);
        }
    }

    /** Direct publish (used by tests/tools); production flow goes through the outbox. */
    public void publishTransferCompleted(Transaction tx, LedgerEntry debit, LedgerEntry credit) {
        publish(TRANSFER_COMPLETED, transferCompletedPayload(tx, debit, credit), "transfer-" + tx.getId());
    }

    /** POSTs a raw JSON payload to QStash. Throws RestClientException on any failure. */
    public void publish(String eventType, String jsonPayload, String deduplicationId) {
        restClient.post()
                .uri(topicUrl)
                .header("Authorization", "Bearer " + token)
                .header("Upstash-Deduplication-Id", deduplicationId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(jsonPayload)
                .retrieve()
                .toBodilessEntity();
    }
}
