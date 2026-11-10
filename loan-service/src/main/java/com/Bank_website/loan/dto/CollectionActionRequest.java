package com.Bank_website.loan.dto;

import jakarta.validation.constraints.NotBlank;

public record CollectionActionRequest(
        @NotBlank(message = "actionType is required")
        String actionType,

        String notes
) {
}
