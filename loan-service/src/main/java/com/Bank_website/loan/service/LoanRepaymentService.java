package com.Bank_website.loan.service;

import com.Bank_website.loan.dto.AccountSummaryDto;
import com.Bank_website.loan.dto.RepaymentResponse;
import com.Bank_website.loan.dto.TransferRequestDto;
import com.Bank_website.loan.dto.TransferResponseDto;
import com.Bank_website.loan.entity.LoanApplication;
import com.Bank_website.loan.entity.LoanRepayment;
import com.Bank_website.loan.exception.ForbiddenException;
import com.Bank_website.loan.exception.InvalidLoanStateException;
import com.Bank_website.loan.exception.ResourceNotFoundException;
import com.Bank_website.loan.mapper.LoanMapper;
import com.Bank_website.loan.repository.LoanApplicationRepository;
import com.Bank_website.loan.repository.LoanRepaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class LoanRepaymentService {

    private static final Logger log = LoggerFactory.getLogger(LoanRepaymentService.class);

    private final LoanRepaymentRepository loanRepaymentRepository;
    private final LoanApplicationRepository loanApplicationRepository;
    private final CustomerClient customerClient;
    private final LedgerClient ledgerClient;
    private final LoanEventPublisher loanEventPublisher;
    private final LoanMapper loanMapper;
    private final UUID bankSettlementAccountId;

    public LoanRepaymentService(LoanRepaymentRepository loanRepaymentRepository,
                                LoanApplicationRepository loanApplicationRepository,
                                CustomerClient customerClient,
                                LedgerClient ledgerClient,
                                LoanEventPublisher loanEventPublisher,
                                LoanMapper loanMapper,
                                @Value("${bank.settlement.account.id:00000000-0000-0000-0000-000000000001}") String bankSettlementAccountId) {
        this.loanRepaymentRepository = loanRepaymentRepository;
        this.loanApplicationRepository = loanApplicationRepository;
        this.customerClient = customerClient;
        this.ledgerClient = ledgerClient;
        this.loanEventPublisher = loanEventPublisher;
        this.loanMapper = loanMapper;
        this.bankSettlementAccountId = UUID.fromString(bankSettlementAccountId);
    }

    public RepaymentResponse payRepayment(UUID repaymentId, UUID customerId) {
        LoanRepayment repayment = loanRepaymentRepository.findById(repaymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Loan repayment not found: " + repaymentId));

        LoanApplication application = repayment.getApplication();
        if (application == null || !application.getCustomerId().equals(customerId)) {
            throw new ForbiddenException("Repayment does not belong to the calling customer");
        }

        if (!"PENDING".equalsIgnoreCase(repayment.getStatus()) && !"OVERDUE".equalsIgnoreCase(repayment.getStatus())) {
            throw new InvalidLoanStateException("Repayment is already " + repayment.getStatus() + "; only PENDING or OVERDUE can be paid");
        }

        List<AccountSummaryDto> accounts = customerClient.getAccounts(customerId);
        if (accounts == null || accounts.isEmpty()) {
            throw new ResourceNotFoundException("Customer has no accounts for repayment");
        }

        UUID fromAccount = accounts.stream()
                .filter(a -> a.status() == null || "ACTIVE".equalsIgnoreCase(a.status()))
                .map(AccountSummaryDto::accountId)
                .findFirst()
                .orElseGet(() -> accounts.get(0).accountId());

        String idempotencyKey = "loan-repay-" + repaymentId;
        TransferRequestDto transferRequest = new TransferRequestDto(
                fromAccount,
                bankSettlementAccountId,
                repayment.getTotalDueCents(),
                idempotencyKey
        );

        TransferResponseDto transferResponse = ledgerClient.postTransfer(transferRequest);

        repayment.setStatus("PAID");
        repayment.setPaidAt(OffsetDateTime.now());
        repayment.setPaymentTransactionId(transferResponse.transactionId());
        repayment = loanRepaymentRepository.save(repayment);

        List<LoanRepayment> allRepayments = loanRepaymentRepository.findByApplicationIdOrderByDueDateAsc(application.getApplicationId());
        boolean allPaid = allRepayments.stream().allMatch(r -> "PAID".equalsIgnoreCase(r.getStatus()));
        if (allPaid && !"CLOSED".equalsIgnoreCase(application.getStatus())) {
            application.setStatus("CLOSED");
            loanApplicationRepository.save(application);
        }

        loanEventPublisher.publishRepaymentPaid(repayment);

        return loanMapper.toRepaymentResponse(repayment);
    }

    public int markOverdueRepayments() {
        LocalDate today = LocalDate.now();
        List<LoanRepayment> pendingPastDue = loanRepaymentRepository.findByStatusAndDueDateBefore("PENDING", today);
        log.info("Found {} pending repayments past due date {}", pendingPastDue.size(), today);

        int count = 0;
        for (LoanRepayment repayment : pendingPastDue) {
            repayment.setStatus("OVERDUE");
            loanRepaymentRepository.save(repayment);
            loanEventPublisher.publishRepaymentOverdue(repayment);
            count++;
        }
        return count;
    }
}
