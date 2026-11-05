package com.Bank_website.fraud.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.UUID;

public record FraudCheckResult(
        @JsonProperty("transaction_id") UUID transactionId,
        boolean flagged,
        List<String> rules
) {
}
