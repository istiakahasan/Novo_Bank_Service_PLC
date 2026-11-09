package com.Bank_website.loan.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateLoanApplicationRequest(
        @NotBlank(message = "productCode is required")
        String productCode,

        @NotNull(message = "requestedAmountCents is required")
        @Positive(message = "requestedAmountCents must be positive")
        Long requestedAmountCents,

        @NotNull(message = "requestedTenureMonths is required")
        @Positive(message = "requestedTenureMonths must be positive")
        Integer requestedTenureMonths
) {
}
