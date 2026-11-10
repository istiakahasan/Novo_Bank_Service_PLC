package com.Bank_website.cards.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AuthorizeCardRequest(
        @NotBlank(message = "cardNumber is required")
        String cardNumber,

        @NotBlank(message = "cvv is required")
        String cvv,

        @NotNull(message = "amountCents is required")
        @Min(value = 1, message = "amountCents must be greater than zero")
        Long amountCents,

        @NotBlank(message = "merchantName is required")
        String merchantName,

        String merchantCategory
) {
}
