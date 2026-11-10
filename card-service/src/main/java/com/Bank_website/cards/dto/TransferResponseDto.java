package com.Bank_website.cards.dto;

import java.time.Instant;
import java.util.UUID;

public record TransferResponseDto(
        UUID transactionId,
        String status,
        UUID fromAccountId,
        UUID toAccountId,
        Long amountCents,
        Instant timestamp
) {
}
