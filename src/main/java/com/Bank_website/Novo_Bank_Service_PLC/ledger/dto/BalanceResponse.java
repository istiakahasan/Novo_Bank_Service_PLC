package com.Bank_website.Novo_Bank_Service_PLC.ledger.dto;

import java.util.UUID;

public record BalanceResponse(
        UUID accountId,
        Long balanceCents,
        String currency
) {
}
