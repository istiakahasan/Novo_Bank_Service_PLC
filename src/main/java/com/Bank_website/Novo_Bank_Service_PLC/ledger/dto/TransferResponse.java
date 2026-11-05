package com.Bank_website.Novo_Bank_Service_PLC.ledger.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TransferResponse(
        UUID transactionId,
        String status,
        Long amountCents,
        OffsetDateTime createdAt
) {
}
