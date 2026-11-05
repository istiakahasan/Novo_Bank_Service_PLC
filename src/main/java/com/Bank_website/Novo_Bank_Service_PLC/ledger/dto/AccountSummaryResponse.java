package com.Bank_website.Novo_Bank_Service_PLC.ledger.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/** One account in GET /api/accounts?customerId= (SYSTEM only). */
public record AccountSummaryResponse(
        UUID accountId,
        String accountNumber,
        String accountType,
        Long balanceCents,
        String status,
        OffsetDateTime createdAt
) {
}
