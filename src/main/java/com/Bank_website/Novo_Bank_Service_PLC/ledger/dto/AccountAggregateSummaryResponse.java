package com.Bank_website.Novo_Bank_Service_PLC.ledger.dto;

public record AccountAggregateSummaryResponse(
        Long totalDepositsCents,
        Long totalLiabilitiesCents,
        Long accountCount
) {
    public AccountAggregateSummaryResponse(Long totalDepositsCents, Long accountCount) {
        this(totalDepositsCents, totalDepositsCents, accountCount);
    }
}
