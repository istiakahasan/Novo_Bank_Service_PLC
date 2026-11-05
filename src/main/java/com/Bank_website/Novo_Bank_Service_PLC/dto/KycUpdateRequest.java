package com.Bank_website.Novo_Bank_Service_PLC.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record KycUpdateRequest(
        @NotBlank @Pattern(regexp = "^(verified|rejected)$", message = "must be 'verified' or 'rejected'") String kycStatus,
        @NotBlank String verifiedBy
) {
}
