package com.Bank_website.rails.dto;

import java.time.Instant;
import java.util.UUID;

public record ExternalPaymentResponse(
        UUID paymentId,
        String direction,
        String rail,
        UUID customerId,
        UUID internalAccountId,
        String externalAccountNumber,
        String externalRoutingNumber,
        String externalBankName,
        Long amountCents,
        String currency,
        String status,
        UUID ledgerTransactionId,
        String failureReason,
        Instant scheduledAt,
        Instant completedAt,
        String idempotencyKey,
        Instant createdAt
) {
}
