package com.Bank_website.Novo_Bank_Service_PLC.ledger;

import com.Bank_website.Novo_Bank_Service_PLC.ledger.dto.TransferRequest;
import com.Bank_website.Novo_Bank_Service_PLC.ledger.dto.TransferResponse;
import com.Bank_website.Novo_Bank_Service_PLC.ledger.entity.Account;
import com.Bank_website.Novo_Bank_Service_PLC.ledger.entity.LedgerEntry;
import com.Bank_website.Novo_Bank_Service_PLC.ledger.entity.OutboxEvent;
import com.Bank_website.Novo_Bank_Service_PLC.ledger.entity.Transaction;
import com.Bank_website.Novo_Bank_Service_PLC.ledger.event.EventPublisher;
import com.Bank_website.Novo_Bank_Service_PLC.ledger.exception.ConflictException;
import com.Bank_website.Novo_Bank_Service_PLC.ledger.exception.InsufficientFundsException;
import com.Bank_website.Novo_Bank_Service_PLC.ledger.mapper.TransferMapper;
import com.Bank_website.Novo_Bank_Service_PLC.ledger.repository.AccountRepository;
import com.Bank_website.Novo_Bank_Service_PLC.ledger.repository.LedgerEntryRepository;
import com.Bank_website.Novo_Bank_Service_PLC.ledger.repository.OutboxRepository;
import com.Bank_website.Novo_Bank_Service_PLC.ledger.repository.TransactionRepository;
import com.Bank_website.Novo_Bank_Service_PLC.ledger.service.OutboxPublisher;
import com.Bank_website.Novo_Bank_Service_PLC.ledger.service.TransferService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

@SuppressWarnings({"unchecked", "rawtypes"})
class TransferServiceTest {

    private final AccountRepository accounts = mock(AccountRepository.class);
    private final TransactionRepository txs = mock(TransactionRepository.class);
    private final LedgerEntryRepository entries = mock(LedgerEntryRepository.class);
    private final OutboxRepository outbox = mock(OutboxRepository.class);
    private final RedisTemplate<String, String> redis = mock(RedisTemplate.class);
    private final ValueOperations<String, String> ops = mock(ValueOperations.class);
    private final EventPublisher publisher = mock(EventPublisher.class);
    private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());

    private TransferService service;
    private final UUID from = UUID.randomUUID();
    private final UUID to = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(ops);
        service = new TransferService(accounts, txs, entries, outbox, new TransferMapper(), redis,
                publisher, json, mock(PlatformTransactionManager.class));
    }

    private Account account(UUID id, long balance) {
        Account a = new Account();
        a.setId(id);
        a.setStatus("active");
        a.setBalanceCents(balance);
        return a;
    }

    private TransferRequest req(long amount) {
        return new TransferRequest(from, to, amount, "key-1");
    }

    private void happyStubs(long fromBalance) {
        when(ops.get("idem:key-1")).thenReturn(null);
        when(ops.setIfAbsent(eq("lock:" + from), any(), any(Duration.class))).thenReturn(true);
        when(accounts.findById(from)).thenReturn(Optional.of(account(from, fromBalance)));
        when(accounts.findById(to)).thenReturn(Optional.of(account(to, 0)));
        when(txs.saveAndFlush(any(Transaction.class))).thenAnswer(i -> {
            Transaction t = i.getArgument(0);
            t.setId(UUID.randomUUID());
            t.setCreatedAt(java.time.OffsetDateTime.now());
            return t;
        });
        when(entries.save(any(LedgerEntry.class))).thenAnswer(i -> i.getArgument(0));
        when(publisher.transferCompletedPayload(any(), any(), any())).thenReturn("{}");
    }

    @Test
    void successfulTransferWritesDoubleEntryOutboxCachesAndUnlocks() {
        happyStubs(100_000);
        when(accounts.decrementBalance(from, 25_000L)).thenReturn(1);
        when(accounts.incrementBalance(to, 25_000L)).thenReturn(1);

        TransferResponse r = service.executeTransfer(req(25_000));

        assertEquals("completed", r.status());
        assertEquals(25_000L, r.amountCents());
        verify(entries, org.mockito.Mockito.times(2)).save(any(LedgerEntry.class));
        verify(outbox).save(any(OutboxEvent.class));
        verify(ops).set(eq("idem:key-1"), any(String.class), eq(Duration.ofSeconds(3600)));
        verify(redis).execute(any(RedisScript.class), eq(List.of("lock:" + from)), any());
    }

    @Test
    void cachedIdempotencyKeyShortCircuits() throws Exception {
        TransferResponse cached = new TransferResponse(UUID.randomUUID(), "completed", 5L,
                java.time.OffsetDateTime.now());
        when(ops.get("idem:key-1")).thenReturn(json.writeValueAsString(cached));

        TransferResponse r = service.executeTransfer(req(5));

        assertEquals(cached.transactionId(), r.transactionId());
        verify(ops, never()).setIfAbsent(any(), any(), any(Duration.class));
        verify(accounts, never()).decrementBalance(any(), anyLong());
    }

    @Test
    void lockContentionThrowsConflict() {
        when(ops.get("idem:key-1")).thenReturn(null);
        when(ops.setIfAbsent(any(), any(), any(Duration.class))).thenReturn(false);
        assertThrows(ConflictException.class, () -> service.executeTransfer(req(5)));
        verify(accounts, never()).findById(any());
    }

    @Test
    void insufficientFundsFailsAndStillReleasesLock() {
        happyStubs(1_000);
        assertThrows(InsufficientFundsException.class, () -> service.executeTransfer(req(5_000)));
        verify(txs, never()).saveAndFlush(any());
        verify(redis).execute(any(RedisScript.class), anyList(), any());
        verify(ops, never()).set(any(), any(), any(Duration.class));
    }

    @Test
    void concurrentDebitReturningZeroRowsIsInsufficientFunds() {
        happyStubs(100_000);
        when(accounts.decrementBalance(from, 25_000L)).thenReturn(0);
        assertThrows(InsufficientFundsException.class, () -> service.executeTransfer(req(25_000)));
        verify(outbox, never()).save(any());
        verify(redis).execute(any(RedisScript.class), anyList(), any());
    }

    @Test
    void duplicateKeyAtDatabaseReturnsOriginalTransaction() {
        happyStubs(100_000);
        doThrow(new DataIntegrityViolationException("dup")).when(txs).saveAndFlush(any(Transaction.class));
        Transaction original = new Transaction();
        original.setId(UUID.randomUUID());
        original.setStatus("completed");
        original.setAmountCents(25_000L);
        when(txs.findByIdempotencyKey("key-1")).thenReturn(Optional.of(original));

        TransferResponse r = service.executeTransfer(req(25_000));

        assertEquals(original.getId(), r.transactionId());
        verify(entries, never()).save(any());
    }

    @Test
    void outboxPublisherMarksPublishedOnlyOnSuccess() {
        OutboxEvent ok = new OutboxEvent();
        ok.setId(1L);
        ok.setEventType("transfer.completed");
        ok.setPayload("{}");
        OutboxEvent bad = new OutboxEvent();
        bad.setId(2L);
        bad.setEventType("transfer.completed");
        bad.setPayload("{}");
        when(outbox.findByPublishedFalseOrderByCreatedAtAsc()).thenReturn(List.of(ok, bad));
        doThrow(new RuntimeException("qstash down")).when(publisher).publish(any(), any(), eq("outbox-2"));

        new OutboxPublisher(outbox, publisher).pollOutbox();

        assertTrue(ok.isPublished());
        assertFalse(bad.isPublished());
        verify(outbox).save(ok);
        verify(outbox, never()).save(bad);
    }
}
