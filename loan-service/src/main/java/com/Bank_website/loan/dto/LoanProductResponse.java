package com.Bank_website.loan.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record LoanProductResponse(
        UUID productId,
        String productCode,
        String productName,
        Long minAmountCents,
        Long maxAmountCents,
        Integer minTenureMonths,
        Integer maxTenureMonths,
        Integer baseInterestRateBps,
        Boolean isActive,
        OffsetDateTime createdAt
) {
}
