package com.Bank_website.Novo_Bank_Service_PLC.ledger.service;

import com.Bank_website.Novo_Bank_Service_PLC.ledger.dto.BalanceResponse;
import com.Bank_website.Novo_Bank_Service_PLC.ledger.dto.LedgerEntryResponse;
import com.Bank_website.Novo_Bank_Service_PLC.ledger.dto.TransferRequest;
import com.Bank_website.Novo_Bank_Service_PLC.ledger.dto.TransferResponse;
import com.Bank_website.Novo_Bank_Service_PLC.ledger.entity.Account;
import com.Bank_website.Novo_Bank_Service_PLC.ledger.entity.LedgerEntry;
import com.Bank_website.Novo_Bank_Service_PLC.ledger.entity.OutboxEvent;
import com.Bank_website.Novo_Bank_Service_PLC.ledger.entity.Transaction;
import com.Bank_website.Novo_Bank_Service_PLC.ledger.event.EventPublisher;
import com.Bank_website.Novo_Bank_Service_PLC.ledger.exception.ConflictException;
import com.Bank_website.Novo_Bank_Service_PLC.ledger.exception.InsufficientFundsException;
import com.Bank_website.Novo_Bank_Service_PLC.ledger.exception.ResourceNotFoundException;
import com.Bank_website.Novo_Bank_Service_PLC.ledger.mapper.TransferMapper;
import com.Bank_website.Novo_Bank_Service_PLC.ledger.repository.AccountRepository;
import com.Bank_website.Novo_Bank_Service_PLC.ledger.repository.LedgerEntryRepository;
import com.Bank_website.Novo_Bank_Service_PLC.ledger.repository.OutboxRepository;
import com.Bank_website.Novo_Bank_Service_PLC.ledger.repository.TransactionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Executes double-entry transfers.
 *
 * The database work (steps c-i) runs inside a TransactionTemplate rather than an
 * @Transactional annotation on the same method. With the annotation the Redis lock would be
 * released in the finally block BEFORE the transaction commits, letting a second request read
 * the pre-commit balance. Here the lock is held until after COMMIT.
 */
@Service
public class TransferService {

    static final Duration LOCK_TTL = Duration.ofSeconds(5);
    static final Duration IDEMPOTENCY_TTL = Duration.ofSeconds(3600);
    private static final int MAX_ENTRIES = 100;

    /** Compare-and-delete so we never release a lock that expired and was re-acquired by someone else. */
    private static final RedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final OutboxRepository outboxRepository;
    private final TransferMapper mapper;
    private final RedisTemplate<String, String> redisTemplate;
    private final EventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final KycGateway kycGateway;
    private final TransactionTemplate transactionTemplate;

    public TransferService(AccountRepository accountRepository,
                           TransactionRepository transactionRepository,
                           LedgerEntryRepository ledgerEntryRepository,
                           OutboxRepository outboxRepository,
                           TransferMapper mapper,
                           RedisTemplate<String, String> redisTemplate,
                           @Qualifier("ledgerEventPublisher") EventPublisher eventPublisher,
                           ObjectMapper objectMapper,
                           KycGateway kycGateway,
                           PlatformTransactionManager transactionManager) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.outboxRepository = outboxRepository;
        this.mapper = mapper;
        this.redisTemplate = redisTemplate;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
        this.kycGateway = kycGateway;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /** replayed = true when an earlier request with the same idempotency key already completed. */
    public record TransferOutcome(TransferResponse response, boolean replayed) {
    }

    public TransferResponse executeTransfer(TransferRequest request) {
        return transfer(request).response();
    }

    public TransferOutcome transfer(TransferRequest request) {
        if (request.fromAccount().equals(request.toAccount())) {
            throw new IllegalArgumentException("fromAccount and toAccount must differ");
        }

        // a. Redis idempotency check
        String idemKey = "idem:" + request.idempotencyKey();
        String cached = redisTemplate.opsForValue().get(idemKey);
        if (cached != null) {
            return new TransferOutcome(readCached(cached), true);
        }

        // a2. Sync KYC check BEFORE the lock and the DB transaction (kyc:{accountId}, 600 s):
        //     404 unknown account, 422 not verified, 503 customer service unavailable (no transfer attempted)
        kycGateway.requireVerifiedAccount(request.fromAccount());

        // b. Distributed lock: SET lock:{from} <token> NX EX 5
        String lockKey = "lock:" + request.fromAccount();
        String lockToken = UUID.randomUUID().toString();
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, lockToken, LOCK_TTL);
        if (!Boolean.TRUE.equals(acquired)) {
            throw new ConflictException("Account is busy with another transfer, retry shortly");
        }

        try {
            TransferResponse response;
            boolean replayed = false;
            try {
                response = transactionTemplate.execute(status -> doTransfer(request));
            } catch (DataIntegrityViolationException duplicate) {
                // f. DB-level idempotency: UNIQUE(idempotency_key) fired, return the original.
                Transaction original = transactionRepository.findByIdempotencyKey(request.idempotencyKey())
                        .orElseThrow(() -> duplicate);
                response = mapper.toResponse(original);
                replayed = true;
            }
            // j. Cache after COMMIT so a rolled-back transfer is never cached.
            redisTemplate.opsForValue().set(idemKey, writeCached(response), IDEMPOTENCY_TTL);
            return new TransferOutcome(response, replayed);
        } finally {
            // k. Always release (compare-and-delete)
            redisTemplate.execute(UNLOCK_SCRIPT, List.of(lockKey), lockToken);
        }
    }

    /** Steps c-i, one database transaction. Any RuntimeException rolls everything back. */
    private TransferResponse doTransfer(TransferRequest request) {
        // c, d. Accounts must exist and be active
        Account from = accountRepository.findById(request.fromAccount())
                .filter(a -> "active".equals(a.getStatus()))
                .orElseThrow(() -> new ResourceNotFoundException("Active account not found: " + request.fromAccount()));
        accountRepository.findById(request.toAccount())
                .filter(a -> "active".equals(a.getStatus()))
                .orElseThrow(() -> new ResourceNotFoundException("Active account not found: " + request.toAccount()));

        // e. Fast-fail balance check (the atomic UPDATE in step h is the real guard)
        if (from.getBalanceCents() < request.amountCents()) {
            throw new InsufficientFundsException("Insufficient funds");
        }

        // f. Transaction row; flush so the UNIQUE constraint fires here
        Transaction tx = new Transaction();
        tx.setIdempotencyKey(request.idempotencyKey());
        tx.setFromAccount(request.fromAccount());
        tx.setToAccount(request.toAccount());
        tx.setAmountCents(request.amountCents());
        tx.setStatus("completed");
        tx = transactionRepository.saveAndFlush(tx);

        // g. Double entry: one debit, one credit (insert only)
        LedgerEntry debit = newEntry(tx, request.fromAccount(), "debit");
        LedgerEntry credit = newEntry(tx, request.toAccount(), "credit");
        debit = ledgerEntryRepository.save(debit);
        credit = ledgerEntryRepository.save(credit);

        // h. Atomic single-statement balance updates
        if (accountRepository.decrementBalance(request.fromAccount(), request.amountCents()) == 0) {
            throw new InsufficientFundsException("Insufficient funds (balance changed concurrently)");
        }
        if (accountRepository.incrementBalance(request.toAccount(), request.amountCents()) == 0) {
            throw new ResourceNotFoundException("Active account not found: " + request.toAccount());
        }

        // i. Outbox row in the same transaction: no event is lost if QStash is down
        OutboxEvent outbox = new OutboxEvent();
        outbox.setEventType(EventPublisher.TRANSFER_COMPLETED);
        outbox.setPayload(eventPublisher.transferCompletedPayload(tx, debit, credit));
        outbox.setPublished(false);
        outboxRepository.save(outbox);

        return mapper.toResponse(tx);
    }

    private LedgerEntry newEntry(Transaction tx, UUID accountId, String direction) {
        LedgerEntry entry = new LedgerEntry();
        entry.setTransactionId(tx.getId());
        entry.setAccountId(accountId);
        entry.setDirection(direction);
        entry.setAmountCents(tx.getAmountCents());
        return entry;
    }

    public TransferResponse getTransfer(UUID id) {
        return transactionRepository.findById(id)
                .map(mapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Transfer not found: " + id));
    }

    public BalanceResponse getBalance(UUID accountId) {
        return accountRepository.findById(accountId)
                .map(mapper::toBalanceResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountId));
    }

    public List<LedgerEntryResponse> getEntries(UUID accountId, int limit) {
        if (!accountRepository.existsById(accountId)) {
            throw new ResourceNotFoundException("Account not found: " + accountId);
        }
        int size = Math.max(1, Math.min(limit, MAX_ENTRIES));
        return ledgerEntryRepository.findByAccountIdOrderByCreatedAtDesc(accountId, PageRequest.of(0, size))
                .stream().map(mapper::toEntryResponse).toList();
    }

    private String writeCached(TransferResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise transfer response", e);
        }
    }

    private TransferResponse readCached(String json) {
        try {
            return objectMapper.readValue(json, TransferResponse.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Corrupt idempotency cache entry", e);
        }
    }
}
