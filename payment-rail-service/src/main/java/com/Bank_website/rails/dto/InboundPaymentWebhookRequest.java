package com.Bank_website.rails.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record InboundPaymentWebhookRequest(
        @NotBlank(message = "rail is required")
        String rail,

        @NotBlank(message = "toAccountNumber is required")
        String toAccountNumber,

        @NotBlank(message = "externalSenderName is required")
        String externalSenderName,

        @NotBlank(message = "externalSenderAccount is required")
        String externalSenderAccount,

        @NotBlank(message = "externalRoutingNumber is required")
        String externalRoutingNumber,

        @NotNull(message = "amountCents is required")
        @Min(value = 1, message = "amountCents must be greater than zero")
        Long amountCents,

        String idempotencyKey
) {
}
