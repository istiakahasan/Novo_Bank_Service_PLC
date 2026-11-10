package com.Bank_website.loan.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CollectionActionResponse(
        UUID actionId,
        UUID applicationId,
        UUID repaymentId,
        String actionType,
        UUID performedBy,
        String notes,
        OffsetDateTime createdAt
) {
}
