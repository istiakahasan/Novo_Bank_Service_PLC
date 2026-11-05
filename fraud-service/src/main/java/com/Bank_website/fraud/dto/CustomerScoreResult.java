package com.Bank_website.fraud.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record CustomerScoreResult(
        @JsonProperty("customer_id") UUID customerId,
        @JsonProperty("aml_risk_rating") String amlRiskRating,
        boolean escalated
) {
}
