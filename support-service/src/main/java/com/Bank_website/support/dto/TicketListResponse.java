package com.Bank_website.support.dto;

import java.util.List;

public record TicketListResponse(
        List<TicketResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
