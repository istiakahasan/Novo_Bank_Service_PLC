package com.Bank_website.loan.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record AddCollateralRequest(
        @NotBlank(message = "collateralType is required")
        String collateralType,

        @NotBlank(message = "description is required")
        String description,

        @NotNull(message = "estimatedValueCents is required")
        @Min(value = 1, message = "estimatedValueCents must be positive")
        Long estimatedValueCents,

        @NotNull(message = "valuationDate is required")
        LocalDate valuationDate
) {
}
