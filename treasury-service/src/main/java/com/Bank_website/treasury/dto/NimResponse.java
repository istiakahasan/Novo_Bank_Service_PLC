package com.Bank_website.treasury.dto;

import java.time.OffsetDateTime;

public record NimResponse(
        Integer nimBps,
        Long interestIncomeCents,
        Long interestExpenseCents,
        Long avgEarningAssetsCents,
        OffsetDateTime computedAt
) {
}
