package com.Bank_website.support.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TicketMessageResponse(
        UUID messageId,
        UUID ticketId,
        UUID senderId,
        String senderType,
        String messageBody,
        boolean isInternalNote,
        OffsetDateTime createdAt
) {
}
