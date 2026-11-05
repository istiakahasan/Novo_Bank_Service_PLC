package com.Bank_website.Novo_Bank_Service_PLC.ledger.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record TransferRequest(
        @NotNull UUID fromAccount,
        @NotNull UUID toAccount,
        @NotNull @Positive Long amountCents,
        @NotBlank @Size(max = 100) String idempotencyKey
) {
}
