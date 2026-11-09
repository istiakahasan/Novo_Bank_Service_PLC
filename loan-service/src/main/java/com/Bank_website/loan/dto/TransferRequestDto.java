package com.Bank_website.loan.dto;

import java.util.UUID;

public record TransferRequestDto(
        UUID fromAccount,
        UUID toAccount,
        Long amountCents,
        String idempotencyKey
) {
}
