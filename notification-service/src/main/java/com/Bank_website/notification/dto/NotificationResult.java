package com.Bank_website.notification.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record NotificationResult(
        @JsonProperty("reference_id") String referenceId,
        @JsonProperty("email_sent") boolean emailSent,
        @JsonProperty("sms_logged") boolean smsLogged,
        boolean duplicate
) {
}
