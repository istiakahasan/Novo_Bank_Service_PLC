package com.Bank_website.loan.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TransferResponseDto(
        UUID transactionId,
        String status,
        Long amountCents,
        OffsetDateTime createdAt
) {
}
