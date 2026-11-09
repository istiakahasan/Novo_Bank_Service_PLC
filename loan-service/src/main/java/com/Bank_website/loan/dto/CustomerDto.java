package com.Bank_website.loan.dto;

import java.util.UUID;

public record CustomerDto(
        UUID id,
        String customerNumber,
        String displayName,
        String mobileNo,
        String email,
        String kycStatus,
        String amlRiskRating,
        String status
) {
}
