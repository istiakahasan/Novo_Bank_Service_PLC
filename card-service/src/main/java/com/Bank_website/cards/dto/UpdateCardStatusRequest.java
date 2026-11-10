package com.Bank_website.cards.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateCardStatusRequest(
        @NotBlank(message = "status is required")
        String status
) {
}
