package com.Bank_website.Novo_Bank_Service_PLC.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record AmlRiskUpdateRequest(
        @NotBlank @Pattern(regexp = "^(low|medium|high)$", message = "must be low, medium or high") String amlRiskRating
) {
}
