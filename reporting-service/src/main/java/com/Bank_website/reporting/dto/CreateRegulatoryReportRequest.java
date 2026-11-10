package com.Bank_website.reporting.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;

public record CreateRegulatoryReportRequest(
        @NotBlank(message = "reportType is required")
        @Pattern(regexp = "^(DAILY_TRANSACTION_SUMMARY|LOAN_PORTFOLIO|KYC_STATUS)$",
                message = "reportType must be DAILY_TRANSACTION_SUMMARY, LOAN_PORTFOLIO, or KYC_STATUS")
        String reportType,

        @NotNull(message = "periodStart is required")
        LocalDate periodStart,

        @NotNull(message = "periodEnd is required")
        LocalDate periodEnd
) {
}
