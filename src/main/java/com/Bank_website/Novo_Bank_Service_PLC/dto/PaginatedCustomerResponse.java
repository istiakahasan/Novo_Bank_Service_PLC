package com.Bank_website.Novo_Bank_Service_PLC.dto;

import java.util.List;

public record PaginatedCustomerResponse(
        List<CustomerResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
