package com.Bank_website.notification.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CustomerCreatedEvent(
        @JsonProperty("event") String event,
        @NotNull @JsonProperty("customer_id") UUID customerId,
        @JsonProperty("customer_number") String customerNumber,
        @JsonProperty("display_name") String displayName,
        @JsonProperty("email") String email,
        @JsonProperty("mobile_no") String mobileNo
) {
}
