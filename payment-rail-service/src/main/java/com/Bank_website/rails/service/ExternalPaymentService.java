package com.Bank_website.rails.service;

import com.Bank_website.rails.dto.AccountSummaryDto;
import com.Bank_website.rails.dto.CreateOutboundPaymentRequest;
import com.Bank_website.rails.dto.ExternalPaymentResponse;
import com.Bank_website.rails.dto.InboundPaymentWebhookRequest;
import com.Bank_website.rails.dto.PageResponse;
import com.Bank_website.rails.dto.TransferRequestDto;
import com.Bank_website.rails.dto.TransferResponseDto;
import com.Bank_website.rails.entity.ExternalPayment;
import com.Bank_website.rails.exception.ForbiddenException;
import com.Bank_website.rails.exception.RailLimitExceededException;
import com.Bank_website.rails.exception.ResourceNotFoundException;
import com.Bank_website.rails.mapper.ExternalPaymentMapper;
import com.Bank_website.rails.repository.ExternalPaymentRepository;
import com.Bank_website.security.AuthPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class ExternalPaymentService {

    private static final Logger log = LoggerFactory.getLogger(ExternalPaymentService.class);

    private static final Set<String> SUPPORTED_RAILS = Set.of("ACH", "RTGS", "SWIFT_SIM");

    private static final Map<String, Long> RAIL_LIMITS = Map.of(
            "ACH", 1_000_000L,        // $10,000
            "RTGS", 100_000_000L,     // $1,000,000
            "SWIFT_SIM", 500_000_000L // $5,000,000
    );

    private final ExternalPaymentRepository paymentRepository;
    private final CustomerClient customerClient;
    private final LedgerClient ledgerClient;
    private final ExternalPaymentMapper mapper;
    private final UUID railSettlementAccountId;

    public ExternalPaymentService(ExternalPaymentRepository paymentRepository,
                                  CustomerClient customerClient,
                                  LedgerClient ledgerClient,
                                  ExternalPaymentMapper mapper,
                                  @Value("${rail.settlement-account-id}") UUID railSettlementAccountId) {
        this.paymentRepository = paymentRepository;
        this.customerClient = customerClient;
        this.ledgerClient = ledgerClient;
        this.mapper = mapper;
        this.railSettlementAccountId = railSettlementAccountId;
    }

    @Transactional
    public ExternalPaymentResponse createOutboundPayment(UUID customerId, CreateOutboundPaymentRequest request) {
        String rail = request.rail().toUpperCase();
        if (!SUPPORTED_RAILS.contains(rail)) {
            throw new IllegalArgumentException("Unsupported payment rail: " + request.rail());
        }

        // Idempotency check
        if (request.idempotencyKey() != null && !request.idempotencyKey().isBlank()) {
            Optional<ExternalPayment> existing = paymentRepository.findByIdempotencyKey(request.idempotencyKey());
            if (existing.isPresent()) {
                ExternalPayment payment = existing.get();
                if (payment.getCustomerId() != null && !payment.getCustomerId().equals(customerId)) {
                    throw new ForbiddenException("Idempotency key collision across different customers");
                }
                log.info("Idempotent request returning existing outbound payment {}", payment.getPaymentId());
                return mapper.toResponse(payment);
            }
        }

        // Rail limit check
        long limit = RAIL_LIMITS.getOrDefault(rail, 0L);
        if (request.amountCents() > limit) {
            throw new RailLimitExceededException("Amount " + request.amountCents() + " exceeds limit " + limit + " for rail " + rail);
        }

        // Verify account belongs to customer
        List<AccountSummaryDto> customerAccounts = customerClient.getAccounts(customerId);
        boolean ownsAccount = customerAccounts.stream()
                .anyMatch(a -> a.accountId().equals(request.fromAccountId()));
        if (!ownsAccount) {
            throw new ForbiddenException("Account " + request.fromAccountId() + " does not belong to customer " + customerId);
        }

        // Record initial payment in PENDING state
        ExternalPayment payment = new ExternalPayment();
        payment.setDirection("OUTBOUND");
        payment.setRail(rail);
        payment.setCustomerId(customerId);
        payment.setInternalAccountId(request.fromAccountId());
        payment.setExternalAccountNumber(request.externalAccountNumber());
        payment.setExternalRoutingNumber(request.externalRoutingNumber());
        payment.setExternalBankName(request.externalBankName());
        payment.setAmountCents(request.amountCents());
        payment.setCurrency("USD");
        payment.setStatus("PENDING");
        payment.setIdempotencyKey(request.idempotencyKey());
        payment = paymentRepository.save(payment);

        // Debit internal account via Ledger
        TransferRequestDto transfer = new TransferRequestDto(
                request.fromAccountId(),
                railSettlementAccountId,
                request.amountCents(),
                "ext-out-" + payment.getPaymentId(),
                "Outbound " + rail + " to " + request.externalBankName()
        );
        TransferResponseDto transferResponse = ledgerClient.postTransfer(transfer);

        // Advance to PROCESSING
        payment.setLedgerTransactionId(transferResponse.transactionId());
        payment.setStatus("PROCESSING");
        payment.setScheduledAt(Instant.now());
        payment = paymentRepository.save(payment);

        return mapper.toResponse(payment);
    }

    @Transactional(readOnly = true)
    public ExternalPaymentResponse getPaymentById(UUID paymentId, AuthPrincipal principal) {
        ExternalPayment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + paymentId));

        if (!principal.hasRole("STAFF")) {
            if (payment.getCustomerId() == null || !payment.getCustomerId().equals(principal.customerId())) {
                throw new ForbiddenException("Access denied to payment: " + paymentId);
            }
        }
        return mapper.toResponse(payment);
    }

    @Transactional(readOnly = true)
    public PageResponse<ExternalPaymentResponse> listPayments(AuthPrincipal principal, Pageable pageable) {
        Page<ExternalPayment> page;
        if (!principal.hasRole("STAFF")) {
            page = paymentRepository.findByCustomerId(principal.customerId(), pageable);
        } else {
            page = paymentRepository.findAll(pageable);
        }
        return PageResponse.from(page.map(mapper::toResponse));
    }

    @Transactional
    public ExternalPaymentResponse processInboundPayment(InboundPaymentWebhookRequest request) {
        String rail = request.rail().toUpperCase();
        if (!SUPPORTED_RAILS.contains(rail)) {
            throw new IllegalArgumentException("Unsupported payment rail: " + request.rail());
        }

        // Idempotency check
        if (request.idempotencyKey() != null && !request.idempotencyKey().isBlank()) {
            Optional<ExternalPayment> existing = paymentRepository.findByIdempotencyKey(request.idempotencyKey());
            if (existing.isPresent()) {
                log.info("Idempotent request returning existing inbound payment {}", existing.get().getPaymentId());
                return mapper.toResponse(existing.get());
            }
        }

        // Resolve internal account by account number
        AccountSummaryDto account = ledgerClient.getAccountByAccountNumber(request.toAccountNumber())
                .orElseThrow(() -> new ResourceNotFoundException("Internal account not found for account number: " + request.toAccountNumber()));

        // Create inbound payment record
        ExternalPayment payment = new ExternalPayment();
        payment.setDirection("INBOUND");
        payment.setRail(rail);
        payment.setCustomerId(account.customerId());
        payment.setInternalAccountId(account.accountId());
        payment.setExternalAccountNumber(request.externalSenderAccount());
        payment.setExternalRoutingNumber(request.externalRoutingNumber());
        payment.setExternalBankName(request.externalSenderName());
        payment.setAmountCents(request.amountCents());
        payment.setCurrency("USD");
        payment.setStatus("PROCESSING");
        payment.setIdempotencyKey(request.idempotencyKey());
        payment.setScheduledAt(Instant.now());
        payment = paymentRepository.save(payment);

        // Credit internal customer account from settlement account
        TransferRequestDto transfer = new TransferRequestDto(
                railSettlementAccountId,
                account.accountId(),
                request.amountCents(),
                "ext-in-" + payment.getPaymentId(),
                "Inbound " + rail + " from " + request.externalSenderName()
        );
        TransferResponseDto transferResponse = ledgerClient.postTransfer(transfer);

        // Mark COMPLETED
        payment.setLedgerTransactionId(transferResponse.transactionId());
        payment.setStatus("COMPLETED");
        payment.setCompletedAt(Instant.now());
        payment = paymentRepository.save(payment);

        return mapper.toResponse(payment);
    }
}
