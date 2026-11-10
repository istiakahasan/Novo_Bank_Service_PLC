package com.Bank_website.rails.dto;

import java.util.UUID;

public record TransferRequestDto(
        UUID fromAccountId,
        UUID toAccountId,
        Long amountCents,
        String idempotencyKey,
        String description
) {
}
