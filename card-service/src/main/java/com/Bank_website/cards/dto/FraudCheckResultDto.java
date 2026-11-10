package com.Bank_website.cards.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FraudCheckResultDto(
        @JsonProperty("transaction_id")
        UUID transactionId,
        boolean flagged,
        List<String> rules
) {
}
