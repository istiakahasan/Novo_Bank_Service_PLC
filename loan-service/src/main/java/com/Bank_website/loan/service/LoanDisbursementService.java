package com.Bank_website.loan.service;

import com.Bank_website.loan.dto.AccountSummaryDto;
import com.Bank_website.loan.dto.LoanApplicationResponse;
import com.Bank_website.loan.dto.TransferRequestDto;
import com.Bank_website.loan.dto.TransferResponseDto;
import com.Bank_website.loan.entity.LoanApplication;
import com.Bank_website.loan.entity.LoanRepayment;
import com.Bank_website.loan.exception.InvalidLoanStateException;
import com.Bank_website.loan.exception.ResourceNotFoundException;
import com.Bank_website.loan.mapper.LoanMapper;
import com.Bank_website.loan.repository.LoanApplicationRepository;
import com.Bank_website.loan.repository.LoanRepaymentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class LoanDisbursementService {

    private final LoanApplicationRepository loanApplicationRepository;
    private final LoanRepaymentRepository loanRepaymentRepository;
    private final CustomerClient customerClient;
    private final LedgerClient ledgerClient;
    private final LoanEventPublisher loanEventPublisher;
    private final LoanMapper loanMapper;
    private final UUID bankSettlementAccountId;

    public LoanDisbursementService(LoanApplicationRepository loanApplicationRepository,
                                  LoanRepaymentRepository loanRepaymentRepository,
                                  CustomerClient customerClient,
                                  LedgerClient ledgerClient,
                                  LoanEventPublisher loanEventPublisher,
                                  LoanMapper loanMapper,
                                  @Value("${bank.settlement.account.id:00000000-0000-0000-0000-000000000001}") String bankSettlementAccountId) {
        this.loanApplicationRepository = loanApplicationRepository;
        this.loanRepaymentRepository = loanRepaymentRepository;
        this.customerClient = customerClient;
        this.ledgerClient = ledgerClient;
        this.loanEventPublisher = loanEventPublisher;
        this.loanMapper = loanMapper;
        this.bankSettlementAccountId = UUID.fromString(bankSettlementAccountId);
    }

    public LoanApplicationResponse disburseLoan(UUID applicationId) {
        LoanApplication application = loanApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Loan application not found: " + applicationId));

        if (!"APPROVED".equalsIgnoreCase(application.getStatus())) {
            throw new InvalidLoanStateException("Loan application must be APPROVED to disburse (current: " + application.getStatus() + ")");
        }

        List<AccountSummaryDto> accounts = customerClient.getAccounts(application.getCustomerId());
        if (accounts == null || accounts.isEmpty()) {
            throw new ResourceNotFoundException("Customer has no accounts for disbursement");
        }

        UUID toAccount = accounts.stream()
                .filter(a -> a.status() == null || "ACTIVE".equalsIgnoreCase(a.status()))
                .map(AccountSummaryDto::accountId)
                .findFirst()
                .orElseGet(() -> accounts.get(0).accountId());

        String idempotencyKey = "loan-disburse-" + applicationId;
        TransferRequestDto transferRequest = new TransferRequestDto(
                bankSettlementAccountId,
                toAccount,
                application.getApprovedAmountCents(),
                idempotencyKey
        );

        TransferResponseDto transferResponse = ledgerClient.postTransfer(transferRequest);

        OffsetDateTime disbursedAt = OffsetDateTime.now();
        application.setStatus("DISBURSED");
        application.setDisbursedAt(disbursedAt);
        application.setDisbursementTransactionId(transferResponse.transactionId());
        application = loanApplicationRepository.save(application);

        long principal = application.getApprovedAmountCents();
        int rateBps = application.getApprovedInterestRateBps();
        int tenure = application.getApprovedTenureMonths();

        long totalInterest = Math.round((double) principal * rateBps / 10000.0 * tenure / 12.0);
        long monthlyPrincipal = principal / tenure;
        long monthlyInterest = totalInterest / tenure;

        List<LoanRepayment> repayments = new ArrayList<>();
        for (int i = 1; i <= tenure; i++) {
            long pDue = (i == tenure) ? (principal - monthlyPrincipal * (tenure - 1)) : monthlyPrincipal;
            long iDue = (i == tenure) ? (totalInterest - monthlyInterest * (tenure - 1)) : monthlyInterest;

            LoanRepayment repayment = new LoanRepayment();
            repayment.setApplication(application);
            repayment.setDueDate(disbursedAt.toLocalDate().plusMonths(i));
            repayment.setPrincipalDueCents(pDue);
            repayment.setInterestDueCents(iDue);
            repayment.setTotalDueCents(pDue + iDue);
            repayment.setStatus("PENDING");
            repayments.add(repayment);
        }

        repayments = loanRepaymentRepository.saveAll(repayments);

        loanEventPublisher.publishLoanDisbursed(application);

        return loanMapper.toApplicationResponse(application, repayments);
    }
}
