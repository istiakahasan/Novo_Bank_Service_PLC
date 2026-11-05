package com.Bank_website.Novo_Bank_Service_PLC.security;

import com.Bank_website.Novo_Bank_Service_PLC.ledger.entity.Transaction;
import com.Bank_website.Novo_Bank_Service_PLC.ledger.repository.AccountRepository;
import com.Bank_website.Novo_Bank_Service_PLC.ledger.repository.TransactionRepository;
import com.Bank_website.security.AuthPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Ownership rules used from @PreAuthorize("... @access.xxx(authentication, #id)").
 * A CUSTOMER may only touch rows that belong to the customer id inside their token ("cid" claim).
 * Unknown ids evaluate to false (403), so existence of other people's records is not revealed.
 */
@Component("access")
public class AccessPolicy {

    private final AccountRepository accounts;
    private final TransactionRepository transactions;

    public AccessPolicy(AccountRepository accounts, TransactionRepository transactions) {
        this.accounts = accounts;
        this.transactions = transactions;
    }

    public boolean isOwnCustomer(Authentication authentication, UUID customerId) {
        AuthPrincipal p = AuthPrincipal.of(authentication);
        return p != null && p.customerId() != null && p.customerId().equals(customerId);
    }

    public boolean ownsAccount(Authentication authentication, UUID accountId) {
        AuthPrincipal p = AuthPrincipal.of(authentication);
        if (p == null || p.customerId() == null || accountId == null) {
            return false;
        }
        return accounts.findCustomerIdByAccountId(accountId).map(p.customerId()::equals).orElse(false);
    }

    /** A customer may view a transfer if they own the debited or the credited account. */
    public boolean canViewTransfer(Authentication authentication, UUID transactionId) {
        Optional<Transaction> tx = transactions.findById(transactionId);
        return tx.isPresent()
                && (ownsAccount(authentication, tx.get().getFromAccount())
                || ownsAccount(authentication, tx.get().getToAccount()));
    }
}
