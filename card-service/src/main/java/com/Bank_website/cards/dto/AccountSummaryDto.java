package com.Bank_website.cards.dto;

import java.util.UUID;

public record AccountSummaryDto(
        UUID accountId,
        UUID customerId,
        String accountNumber,
        String accountType,
        Long balanceCents,
        String status
) {
}
