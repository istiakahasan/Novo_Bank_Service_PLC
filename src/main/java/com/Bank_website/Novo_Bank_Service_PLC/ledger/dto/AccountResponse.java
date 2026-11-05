package com.Bank_website.Novo_Bank_Service_PLC.ledger.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AccountResponse(
        UUID id,
        UUID customerId,
        String accountNumber,
        String accountType,
        Long balanceCents,
        String status,
        OffsetDateTime createdAt
) {
}
