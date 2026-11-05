package com.Bank_website.Novo_Bank_Service_PLC.ledger.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.UUID;

public record CreateAccountRequest(
        @NotNull UUID customerId,
        @NotNull @Pattern(regexp = "^(savings|checking)$", message = "must be savings or checking") String accountType,
        @NotNull @PositiveOrZero Long initialDepositCents
) {
}
