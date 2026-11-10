package com.Bank_website.treasury.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public record AccountSummaryDto(
        Long totalDepositsCents,
        Long totalLiabilitiesCents,
        Long accountCount
) {
    @JsonCreator
    public AccountSummaryDto(
            @JsonProperty("totalDepositsCents") Long totalDepositsCents,
            @JsonProperty(value = "totalLiabilitiesCents", required = false) Long totalLiabilitiesCents,
            @JsonProperty(value = "accountCount", required = false) Long accountCount
    ) {
        this.totalDepositsCents = totalDepositsCents != null ? totalDepositsCents : 0L;
        this.totalLiabilitiesCents = totalLiabilitiesCents != null ? totalLiabilitiesCents : this.totalDepositsCents;
        this.accountCount = accountCount != null ? accountCount : 0L;
    }
}
