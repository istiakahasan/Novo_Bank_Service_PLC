package com.Bank_website.Novo_Bank_Service_PLC.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Deliberately omits idNumber (PII). */
public record CustomerResponse(
        UUID id,
        String customerNumber,
        String displayName,
        String mobileNo,
        String email,
        String kycStatus,
        String amlRiskRating,
        String status,
        OffsetDateTime createdAt
) {
}
