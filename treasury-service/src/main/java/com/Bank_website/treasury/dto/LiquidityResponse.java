package com.Bank_website.treasury.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record LiquidityResponse(
        Long totalDepositsCents,
        Long totalLoansCents,
        Long liquidAssetsCents,
        BigDecimal lcrRatio,
        OffsetDateTime computedAt
) {
}
