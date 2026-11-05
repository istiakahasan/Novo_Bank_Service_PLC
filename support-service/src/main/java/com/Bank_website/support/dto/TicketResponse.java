package com.Bank_website.support.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** messages is present on create / get-by-id and omitted in list responses. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TicketResponse(
        UUID ticketId,
        UUID customerId,
        UUID assignedAgentId,
        String category,
        String priority,
        String status,
        String subject,
        UUID relatedTransactionId,
        OffsetDateTime createdAt,
        OffsetDateTime resolvedAt,
        List<TicketMessageResponse> messages
) {
}
