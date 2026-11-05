package com.Bank_website.notification.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

/** Subset of the Customer Service's CustomerResponse that notifications need. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CustomerInfo(
        UUID id,
        String customerNumber,
        String displayName,
        String mobileNo,
        String email
) {
}
