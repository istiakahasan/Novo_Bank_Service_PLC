package com.Bank_website.loan.service;

import com.Bank_website.loan.dto.CollectionActionRequest;
import com.Bank_website.loan.dto.CollectionActionResponse;
import com.Bank_website.loan.dto.OverdueRepaymentResponse;
import com.Bank_website.loan.entity.CollectionAction;
import com.Bank_website.loan.entity.LoanApplication;
import com.Bank_website.loan.entity.LoanRepayment;
import com.Bank_website.loan.exception.ResourceNotFoundException;
import com.Bank_website.loan.mapper.CollateralMapper;
import com.Bank_website.loan.repository.CollectionActionRepository;
import com.Bank_website.loan.repository.LoanApplicationRepository;
import com.Bank_website.loan.repository.LoanRepaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class CollectionsService {

    private static final Logger log = LoggerFactory.getLogger(CollectionsService.class);

    private static final Set<String> ALLOWED_ACTIONS = Set.of(
            "REMINDER_SENT", "PHONE_CALL", "DEMAND_LETTER", "LEGAL_NOTICE",
            "WRITE_OFF_PROPOSED", "WRITE_OFF_APPROVED"
    );

    private final LoanRepaymentRepository repaymentRepository;
    private final CollectionActionRepository collectionActionRepository;
    private final LoanApplicationRepository applicationRepository;
    private final CollateralMapper collateralMapper;
    private final LoanEventPublisher loanEventPublisher;

    public CollectionsService(LoanRepaymentRepository repaymentRepository,
                              CollectionActionRepository collectionActionRepository,
                              LoanApplicationRepository applicationRepository,
                              CollateralMapper collateralMapper,
                              LoanEventPublisher loanEventPublisher) {
        this.repaymentRepository = repaymentRepository;
        this.collectionActionRepository = collectionActionRepository;
        this.applicationRepository = applicationRepository;
        this.collateralMapper = collateralMapper;
        this.loanEventPublisher = loanEventPublisher;
    }

    @Transactional(readOnly = true)
    public List<OverdueRepaymentResponse> getOverdueRepayments() {
        List<LoanRepayment> overdues = repaymentRepository.findByStatusWithApplicationOrderByDueDateAsc("OVERDUE");
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        List<OverdueRepaymentResponse> result = new ArrayList<>();
        for (LoanRepayment r : overdues) {
            LoanApplication app = r.getApplication();
            UUID appId = app.getApplicationId();
            UUID customerId = app.getCustomerId();
            long daysOverdue = ChronoUnit.DAYS.between(r.getDueDate(), today);

            Optional<CollectionAction> lastAction = collectionActionRepository
                    .findFirstByApplicationIdOrderByCreatedAtDesc(appId);
            String lastActionType = lastAction.map(CollectionAction::getActionType).orElse(null);

            result.add(new OverdueRepaymentResponse(
                    r.getRepaymentId(),
                    appId,
                    customerId,
                    r.getDueDate(),
                    daysOverdue,
                    r.getTotalDueCents(),
                    lastActionType
            ));
        }

        result.sort(Comparator.comparingLong(OverdueRepaymentResponse::daysOverdue).reversed());
        return result;
    }

    @Transactional
    public CollectionActionResponse recordAction(UUID repaymentId, String userSubject, CollectionActionRequest request) {
        LoanRepayment repayment = repaymentRepository.findById(repaymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Repayment not found: " + repaymentId));

        String actionType = request.actionType().toUpperCase();
        if (!ALLOWED_ACTIONS.contains(actionType)) {
            throw new IllegalArgumentException("Invalid collection action type: " + request.actionType());
        }

        LoanApplication application = repayment.getApplication();

        UUID performedBy = null;
        if (userSubject != null && !userSubject.isBlank()) {
            try {
                performedBy = UUID.fromString(userSubject);
            } catch (IllegalArgumentException ignored) {
            }
        }

        CollectionAction action = new CollectionAction();
        action.setApplication(application);
        action.setRepayment(repayment);
        action.setActionType(actionType);
        action.setPerformedBy(performedBy);
        action.setNotes(request.notes());
        action = collectionActionRepository.save(action);

        if ("WRITE_OFF_PROPOSED".equals(actionType)) {
            application.setStatus("UNDER_REVIEW");
            applicationRepository.save(application);
            log.info("Loan application {} flagged as UNDER_REVIEW following write-off proposal", application.getApplicationId());
        } else if ("WRITE_OFF_APPROVED".equals(actionType)) {
            List<LoanRepayment> remaining = repaymentRepository
                    .findPendingOrOverdueByApplicationId(application.getApplicationId());
            for (LoanRepayment r : remaining) {
                r.setStatus("WRITTEN_OFF");
            }
            repaymentRepository.saveAll(remaining);

            application.setStatus("CLOSED");
            applicationRepository.save(application);
            log.info("Loan application {} CLOSED following write-off approval. Marked {} repayments as WRITTEN_OFF.",
                    application.getApplicationId(), remaining.size());
        }

        return collateralMapper.toActionResponse(action);
    }

    @Transactional
    public int runDailyCollections() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate thresholdDate = today.minusDays(30);

        List<LoanRepayment> delinquentList = repaymentRepository
                .findByStatusAndDueDateBefore("OVERDUE", thresholdDate);

        OffsetDateTime sevenDaysAgo = OffsetDateTime.now(ZoneOffset.UTC).minusDays(7);
        int remindersCreated = 0;

        for (LoanRepayment repayment : delinquentList) {
            LoanApplication app = repayment.getApplication();
            UUID appId = app.getApplicationId();

            boolean hasRecentAction = collectionActionRepository.hasRecentFormalAction(appId, sevenDaysAgo);
            if (!hasRecentAction) {
                CollectionAction action = new CollectionAction();
                action.setApplication(app);
                action.setRepayment(repayment);
                action.setActionType("REMINDER_SENT");
                action.setNotes("Auto-reminder: 30+ days overdue");
                collectionActionRepository.save(action);

                loanEventPublisher.publishCollectionReminder(
                        appId,
                        repayment.getRepaymentId(),
                        repayment.getTotalDueCents(),
                        repayment.getDueDate()
                );
                remindersCreated++;
                log.info("Auto-reminder sent for 30+ days overdue repayment {} on loan {}",
                        repayment.getRepaymentId(), appId);
            }
        }

        return remindersCreated;
    }
}
