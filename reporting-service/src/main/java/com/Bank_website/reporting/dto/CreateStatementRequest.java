package com.Bank_website.reporting.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CreateStatementRequest(
        @NotNull(message = "periodStart is required")
        LocalDate periodStart,

        @NotNull(message = "periodEnd is required")
        LocalDate periodEnd
) {
}
