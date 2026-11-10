package com.Bank_website.treasury.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record TreasurySnapshotResponse(
        UUID snapshotId,
        LocalDate snapshotDate,
        Long totalDepositsCents,
        Long totalLoansCents,
        Long liquidAssetsCents,
        BigDecimal lcrRatio,
        Integer nimBps,
        OffsetDateTime createdAt,
        List<GapBucketResponse> gapBuckets
) {
}
