package com.Bank_website.support.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateMessageRequest(
        @NotBlank @Size(max = 5000) String messageBody,
        Boolean isInternalNote
) {
}
