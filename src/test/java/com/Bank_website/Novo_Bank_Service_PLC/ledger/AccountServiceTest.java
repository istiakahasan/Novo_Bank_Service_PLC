package com.Bank_website.Novo_Bank_Service_PLC.ledger;

import com.Bank_website.Novo_Bank_Service_PLC.ledger.dto.AccountResponse;
import com.Bank_website.Novo_Bank_Service_PLC.ledger.dto.CreateAccountRequest;
import com.Bank_website.Novo_Bank_Service_PLC.ledger.entity.Account;
import com.Bank_website.Novo_Bank_Service_PLC.ledger.entity.LedgerEntry;
import com.Bank_website.Novo_Bank_Service_PLC.ledger.entity.OutboxEvent;
import com.Bank_website.Novo_Bank_Service_PLC.ledger.entity.Transaction;
import com.Bank_website.Novo_Bank_Service_PLC.ledger.event.EventPublisher;
import com.Bank_website.Novo_Bank_Service_PLC.ledger.exception.KycNotVerifiedException;
import com.Bank_website.Novo_Bank_Service_PLC.ledger.mapper.TransferMapper;
import com.Bank_website.Novo_Bank_Service_PLC.ledger.repository.AccountRepository;
import com.Bank_website.Novo_Bank_Service_PLC.ledger.repository.LedgerEntryRepository;
import com.Bank_website.Novo_Bank_Service_PLC.ledger.repository.OutboxRepository;
import com.Bank_website.Novo_Bank_Service_PLC.ledger.repository.TransactionRepository;
import com.Bank_website.Novo_Bank_Service_PLC.ledger.service.AccountService;
import com.Bank_website.Novo_Bank_Service_PLC.ledger.service.KycGateway;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class AccountServiceTest {

    private final AccountRepository accounts = mock(AccountRepository.class);
    private final TransactionRepository txs = mock(TransactionRepository.class);
    private final LedgerEntryRepository entries = mock(LedgerEntryRepository.class);
    private final OutboxRepository outbox = mock(OutboxRepository.class);
    private final KycGateway kyc = mock(KycGateway.class);
    private final EventPublisher publisher = mock(EventPublisher.class);
    private final AccountService service =
            new AccountService(accounts, txs, entries, outbox, kyc, publisher, new TransferMapper());

    private final UUID customerId = UUID.randomUUID();

    private void stubSaves() {
        when(accounts.count()).thenReturn(3L);
        when(accounts.saveAndFlush(any(Account.class))).thenAnswer(i -> i.getArgument(0));
        when(txs.saveAndFlush(any(Transaction.class))).thenAnswer(i -> {
            Transaction t = i.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });
        when(publisher.accountCreatedPayload(any(), org.mockito.ArgumentMatchers.anyLong())).thenReturn("{}");
    }

    @Test
    void opensAccountWithDepositLedgerCreditAndOutboxEvent() {
        stubSaves();

        AccountResponse r = service.createAccount(new CreateAccountRequest(customerId, "savings", 50_000L));

        assertEquals("ACC-0000000004", r.accountNumber());
        assertEquals(50_000L, r.balanceCents());
        assertEquals("active", r.status());
        org.mockito.ArgumentCaptor<LedgerEntry> entry = org.mockito.ArgumentCaptor.forClass(LedgerEntry.class);
        verify(entries).save(entry.capture());
        assertEquals("credit", entry.getValue().getDirection());
        assertEquals(50_000L, entry.getValue().getAmountCents());
        verify(outbox).save(any(OutboxEvent.class));
    }

    @Test
    void zeroDepositSkipsLedgerEntryButStillEmitsEvent() {
        stubSaves();
        service.createAccount(new CreateAccountRequest(customerId, "checking", 0L));
        verify(txs, never()).saveAndFlush(any());
        verify(entries, never()).save(any());
        verify(outbox).save(any(OutboxEvent.class));
    }

    @Test
    void unverifiedCustomerCannotOpenAccount() {
        doThrow(new KycNotVerifiedException("Customer KYC is not verified (status: pending)"))
                .when(kyc).requireVerifiedCustomer(customerId);
        assertThrows(KycNotVerifiedException.class,
                () -> service.createAccount(new CreateAccountRequest(customerId, "savings", 100L)));
        verify(accounts, never()).saveAndFlush(any());
    }

    @Test
    void kycGatewayCachesOnlyVerifiedUnderAccountKey() {
        com.Bank_website.Novo_Bank_Service_PLC.ledger.service.KycLookup lookup =
                mock(com.Bank_website.Novo_Bank_Service_PLC.ledger.service.KycLookup.class);
        RedisTemplate<String, String> redis = mock(RedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        KycGateway gateway = new KycGateway(lookup, redis);
        UUID accountId = UUID.randomUUID();

        when(lookup.statusByAccount(accountId)).thenReturn("pending");
        assertThrows(KycNotVerifiedException.class, () -> gateway.requireVerifiedAccount(accountId));
        verify(ops, never()).set(any(), any(), any(Duration.class));

        when(lookup.statusByAccount(accountId)).thenReturn("verified");
        gateway.requireVerifiedAccount(accountId);
        verify(ops).set(eq("kyc:" + accountId), eq("verified"), eq(Duration.ofSeconds(600)));

        // cache hit: no further lookup
        when(ops.get("kyc:" + accountId)).thenReturn("verified");
        gateway.requireVerifiedAccount(accountId);
        verify(lookup, org.mockito.Mockito.times(2)).statusByAccount(accountId);
    }
}
