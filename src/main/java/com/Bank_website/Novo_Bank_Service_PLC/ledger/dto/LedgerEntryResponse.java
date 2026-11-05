package com.Bank_website.Novo_Bank_Service_PLC.ledger.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record LedgerEntryResponse(
        Long id,
        UUID transactionId,
        UUID accountId,
        String direction,
        Long amountCents,
        OffsetDateTime createdAt
) {
}
