package com.Bank_website.cards.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

public record CardResponse(
        UUID cardId,
        UUID customerId,
        UUID accountId,
        @JsonProperty("maskedNumber")
        String maskedNumber,
        Integer expiryMonth,
        Integer expiryYear,
        String status,
        Long dailyLimitCents,
        Instant createdAt
) {
}
