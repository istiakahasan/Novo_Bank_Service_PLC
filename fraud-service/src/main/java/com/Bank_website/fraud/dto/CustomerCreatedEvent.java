package com.Bank_website.fraud.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** is_pep and country_risk are optional enrichment fields; absent means "not known to be risky". */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CustomerCreatedEvent(
        @JsonProperty("event") String event,
        @NotNull @JsonProperty("customer_id") UUID customerId,
        @JsonProperty("customer_number") String customerNumber,
        @JsonProperty("display_name") String displayName,
        @JsonProperty("email") String email,
        @JsonProperty("mobile_no") String mobileNo,
        @JsonProperty("is_pep") Boolean isPep,
        @JsonProperty("country_risk") String countryRisk
) {
}
