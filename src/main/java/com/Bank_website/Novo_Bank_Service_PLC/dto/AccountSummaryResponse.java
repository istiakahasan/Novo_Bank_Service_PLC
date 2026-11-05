package com.Bank_website.Novo_Bank_Service_PLC.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/** One account of a customer, as returned by the Ledger Service. */
public record AccountSummaryResponse(
        UUID accountId,
        String accountNumber,
        String accountType,
        Long balanceCents,
        String status,
        OffsetDateTime createdAt
) {
}
