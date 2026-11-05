package com.Bank_website.Novo_Bank_Service_PLC.ledger.mapper;

import com.Bank_website.Novo_Bank_Service_PLC.ledger.dto.BalanceResponse;
import com.Bank_website.Novo_Bank_Service_PLC.ledger.dto.LedgerEntryResponse;
import com.Bank_website.Novo_Bank_Service_PLC.ledger.dto.TransferResponse;
import com.Bank_website.Novo_Bank_Service_PLC.ledger.entity.Account;
import com.Bank_website.Novo_Bank_Service_PLC.ledger.entity.LedgerEntry;
import com.Bank_website.Novo_Bank_Service_PLC.ledger.entity.Transaction;
import org.springframework.stereotype.Component;

@Component
public class TransferMapper {

    public static final String CURRENCY = "USD";

    public TransferResponse toResponse(Transaction tx) {
        return new TransferResponse(tx.getId(), tx.getStatus(), tx.getAmountCents(), tx.getCreatedAt());
    }

    public BalanceResponse toBalanceResponse(Account account) {
        return new BalanceResponse(account.getId(), account.getBalanceCents(), CURRENCY);
    }

    public LedgerEntryResponse toEntryResponse(LedgerEntry e) {
        return new LedgerEntryResponse(e.getId(), e.getTransactionId(), e.getAccountId(),
                e.getDirection(), e.getAmountCents(), e.getCreatedAt());
    }
}
