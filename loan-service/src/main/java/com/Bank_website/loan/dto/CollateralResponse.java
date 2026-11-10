package com.Bank_website.loan.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CollateralResponse(
        UUID collateralId,
        UUID applicationId,
        String collateralType,
        String description,
        Long estimatedValueCents,
        LocalDate valuationDate,
        String status,
        OffsetDateTime createdAt
) {
}
