package com.Bank_website.Novo_Bank_Service_PLC.ledger.event;

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
        payload.put("eventType", TRANSFER_COMPLETED);
        payload.put("transactionId", tx.getId());
        payload.put("idempotencyKey", tx.getIdempotencyKey());
        payload.put("fromAccount", tx.getFromAccount());
        payload.put("toAccount", tx.getToAccount());
        payload.put("amountCents", tx.getAmountCents());
        payload.put("status", tx.getStatus());
        payload.put("debitEntryId", debit.getId());
        payload.put("creditEntryId", credit.getId());
        payload.put("occurredAt", OffsetDateTime.now().toString());
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
