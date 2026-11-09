package com.Bank_website.loan.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record RepaymentResponse(
        UUID repaymentId,
        UUID applicationId,
        LocalDate dueDate,
        Long principalDueCents,
        Long interestDueCents,
        Long totalDueCents,
        OffsetDateTime paidAt,
        UUID paymentTransactionId,
        String status,
        OffsetDateTime createdAt
) {
}
