package com.Bank_website.support.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateTicketRequest(
        @NotBlank
        @Pattern(regexp = "^(TRANSACTION_DISPUTE|ACCOUNT_LOCK|KYC_VERIFICATION|GENERAL_INQUIRY)$",
                message = "must be TRANSACTION_DISPUTE, ACCOUNT_LOCK, KYC_VERIFICATION or GENERAL_INQUIRY")
        String category,
        @Pattern(regexp = "^(LOW|MEDIUM|HIGH|URGENT)$", message = "must be LOW, MEDIUM, HIGH or URGENT")
        String priority,
        @NotBlank @Size(max = 200) String subject,
        UUID relatedTransactionId
) {
}
