package com.Bank_website.loan.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record LoanApplicationResponse(
        UUID applicationId,
        UUID customerId,
        String productCode,
        String productName,
        Long requestedAmountCents,
        Integer requestedTenureMonths,
        String status,
        Long approvedAmountCents,
        Integer approvedInterestRateBps,
        Integer approvedTenureMonths,
        String rejectionReason,
        UUID reviewedBy,
        OffsetDateTime reviewedAt,
        OffsetDateTime disbursedAt,
        UUID disbursementTransactionId,
        OffsetDateTime createdAt,
        List<RepaymentResponse> repayments
) {
}
