package com.Bank_website.notification.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TransferCompletedEvent(
        @JsonProperty("event") String event,
        @NotNull @JsonProperty("transaction_id") UUID transactionId,
        @NotNull @JsonProperty("from_account") UUID fromAccount,
        @NotNull @JsonProperty("to_account") UUID toAccount,
        @NotNull @Positive @JsonProperty("amount_cents") Long amountCents,
        @JsonProperty("created_at") String createdAt
) {
}
