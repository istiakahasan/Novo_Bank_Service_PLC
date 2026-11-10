package com.Bank_website.cards.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateCardRequest(
        @NotNull(message = "accountId is required")
        UUID accountId
) {
}
