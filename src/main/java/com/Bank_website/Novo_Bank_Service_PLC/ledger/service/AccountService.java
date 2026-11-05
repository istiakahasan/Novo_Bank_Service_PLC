package com.Bank_website.Novo_Bank_Service_PLC.ledger.service;

import com.Bank_website.Novo_Bank_Service_PLC.ledger.dto.AccountResponse;
import com.Bank_website.Novo_Bank_Service_PLC.ledger.dto.CreateAccountRequest;
import com.Bank_website.Novo_Bank_Service_PLC.ledger.entity.Account;
import com.Bank_website.Novo_Bank_Service_PLC.ledger.entity.LedgerEntry;
import com.Bank_website.Novo_Bank_Service_PLC.ledger.entity.OutboxEvent;
import com.Bank_website.Novo_Bank_Service_PLC.ledger.entity.Transaction;
import com.Bank_website.Novo_Bank_Service_PLC.ledger.event.EventPublisher;
import com.Bank_website.Novo_Bank_Service_PLC.ledger.mapper.TransferMapper;
import com.Bank_website.Novo_Bank_Service_PLC.ledger.repository.AccountRepository;
import com.Bank_website.Novo_Bank_Service_PLC.ledger.repository.LedgerEntryRepository;
import com.Bank_website.Novo_Bank_Service_PLC.ledger.repository.OutboxRepository;
import com.Bank_website.Novo_Bank_Service_PLC.ledger.repository.TransactionRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final OutboxRepository outboxRepository;
    private final KycGateway kycGateway;
    private final EventPublisher eventPublisher;
    private final TransferMapper mapper;

    public AccountService(AccountRepository accountRepository,
                          TransactionRepository transactionRepository,
                          LedgerEntryRepository ledgerEntryRepository,
                          OutboxRepository outboxRepository,
                          KycGateway kycGateway,
                          @Qualifier("ledgerEventPublisher") EventPublisher eventPublisher,
                          TransferMapper mapper) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.outboxRepository = outboxRepository;
        this.kycGateway = kycGateway;
        this.eventPublisher = eventPublisher;
        this.mapper = mapper;
    }

    /**
     * Opens an account for a KYC-verified customer. The account row, the opening-deposit ledger
     * credit (with a synthetic transaction, from = to = the new account) and the account.created
     * outbox row are written in ONE database transaction.
     *
     * accountNumber uses count()+1; two simultaneous requests can collide on the UNIQUE constraint,
     * in which case one gets 409 and should retry.
     */
    @Transactional
    public AccountResponse createAccount(CreateAccountRequest request) {
        kycGateway.requireVerified(request.customerId());

        OffsetDateTime now = OffsetDateTime.now();
        Account account = new Account();
        account.setId(UUID.randomUUID());
        account.setCustomerId(request.customerId());
        account.setAccountNumber(String.format("ACC-%010d", accountRepository.count() + 1));
        account.setAccountType(request.accountType());
        account.setBalanceCents(request.initialDepositCents());
        account.setStatus("active");
        account.setCreatedAt(now);
        account.setUpdatedAt(now);
        account = accountRepository.saveAndFlush(account);

        long deposit = request.initialDepositCents();
        if (deposit > 0) {
            Transaction tx = new Transaction();
            tx.setIdempotencyKey("open-" + account.getId());
            tx.setFromAccount(account.getId());
            tx.setToAccount(account.getId());
            tx.setAmountCents(deposit);
            tx.setStatus("completed");
            tx = transactionRepository.saveAndFlush(tx);

            LedgerEntry credit = new LedgerEntry();
            credit.setTransactionId(tx.getId());
            credit.setAccountId(account.getId());
            credit.setDirection("credit");
            credit.setAmountCents(deposit);
            ledgerEntryRepository.save(credit);
        }

        OutboxEvent outbox = new OutboxEvent();
        outbox.setEventType(EventPublisher.ACCOUNT_CREATED);
        outbox.setPayload(eventPublisher.accountCreatedPayload(account, deposit));
        outbox.setPublished(false);
        outboxRepository.save(outbox);

        return mapper.toAccountResponse(account);
    }
}
