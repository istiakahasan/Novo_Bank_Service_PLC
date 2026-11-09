package com.Bank_website.loan.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ReviewLoanRequest(
        @NotBlank(message = "action is required")
        @Pattern(regexp = "^(APPROVE|REJECT)$", message = "action must be APPROVE or REJECT")
        String action,

        Long approvedAmountCents,
        Integer approvedInterestRateBps,
        Integer approvedTenureMonths,
        String rejectionReason
) {
}
