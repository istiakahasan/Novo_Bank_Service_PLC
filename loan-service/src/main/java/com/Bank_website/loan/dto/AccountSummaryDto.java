package com.Bank_website.loan.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AccountSummaryDto(
        UUID accountId,
        String accountNumber,
        String accountType,
        Long balanceCents,
        String status,
        OffsetDateTime createdAt
) {
}
