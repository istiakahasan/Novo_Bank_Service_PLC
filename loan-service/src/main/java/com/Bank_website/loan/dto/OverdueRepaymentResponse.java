package com.Bank_website.loan.dto;

import java.time.LocalDate;
import java.util.UUID;

public record OverdueRepaymentResponse(
        UUID repaymentId,
        UUID applicationId,
        UUID customerId,
        LocalDate dueDate,
        long daysOverdue,
        Long totalDueCents,
        String lastActionType
) {
}
