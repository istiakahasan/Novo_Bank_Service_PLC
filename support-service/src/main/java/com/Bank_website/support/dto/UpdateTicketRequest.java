package com.Bank_website.support.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

public record UpdateTicketRequest(
        @Pattern(regexp = "^(OPEN|IN_PROGRESS|PENDING_CUSTOMER|RESOLVED|CLOSED)$",
                message = "must be OPEN, IN_PROGRESS, PENDING_CUSTOMER, RESOLVED or CLOSED")
        String status,
        UUID assignedAgentId,
        @Pattern(regexp = "^(LOW|MEDIUM|HIGH|URGENT)$", message = "must be LOW, MEDIUM, HIGH or URGENT")
        String priority
) {

    @JsonIgnore
    @AssertTrue(message = "at least one of status, assignedAgentId, priority is required")
    public boolean isAnyFieldPresent() {
        return status != null || assignedAgentId != null || priority != null;
    }
}
