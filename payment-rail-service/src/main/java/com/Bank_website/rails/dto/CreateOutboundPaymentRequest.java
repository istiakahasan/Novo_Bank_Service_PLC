package com.Bank_website.rails.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateOutboundPaymentRequest(
        @NotBlank(message = "rail is required")
        String rail,

        @NotNull(message = "fromAccountId is required")
        UUID fromAccountId,

        @NotBlank(message = "externalAccountNumber is required")
        String externalAccountNumber,

        @NotBlank(message = "externalRoutingNumber is required")
        String externalRoutingNumber,

        @NotBlank(message = "externalBankName is required")
        String externalBankName,

        @NotNull(message = "amountCents is required")
        @Min(value = 1, message = "amountCents must be greater than zero")
        Long amountCents,

        String idempotencyKey
) {
}
