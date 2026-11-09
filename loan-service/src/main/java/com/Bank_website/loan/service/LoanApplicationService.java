package com.Bank_website.loan.service;

import com.Bank_website.loan.dto.CreateLoanApplicationRequest;
import com.Bank_website.loan.dto.CustomerDto;
import com.Bank_website.loan.dto.LoanApplicationResponse;
import com.Bank_website.loan.dto.LoanProductResponse;
import com.Bank_website.loan.dto.ReviewLoanRequest;
import com.Bank_website.loan.entity.LoanApplication;
import com.Bank_website.loan.entity.LoanProduct;
import com.Bank_website.loan.entity.LoanRepayment;
import com.Bank_website.loan.exception.ForbiddenException;
import com.Bank_website.loan.exception.InvalidLoanStateException;
import com.Bank_website.loan.exception.ProductValidationException;
import com.Bank_website.loan.exception.ResourceNotFoundException;
import com.Bank_website.loan.mapper.LoanMapper;
import com.Bank_website.loan.repository.LoanApplicationRepository;
import com.Bank_website.loan.repository.LoanProductRepository;
import com.Bank_website.loan.repository.LoanRepaymentRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class LoanApplicationService {

    private final LoanProductRepository loanProductRepository;
    private final LoanApplicationRepository loanApplicationRepository;
    private final LoanRepaymentRepository loanRepaymentRepository;
    private final LoanMapper loanMapper;
    private final CustomerClient customerClient;
    private final LoanEventPublisher loanEventPublisher;

    public LoanApplicationService(LoanProductRepository loanProductRepository,
                                  LoanApplicationRepository loanApplicationRepository,
                                  LoanRepaymentRepository loanRepaymentRepository,
                                  LoanMapper loanMapper,
                                  CustomerClient customerClient,
                                  LoanEventPublisher loanEventPublisher) {
        this.loanProductRepository = loanProductRepository;
        this.loanApplicationRepository = loanApplicationRepository;
        this.loanRepaymentRepository = loanRepaymentRepository;
        this.loanMapper = loanMapper;
        this.customerClient = customerClient;
        this.loanEventPublisher = loanEventPublisher;
    }

    @Transactional(readOnly = true)
    public List<LoanProductResponse> getActiveProducts() {
        return loanProductRepository.findByIsActiveTrue().stream()
                .map(loanMapper::toProductResponse)
                .toList();
    }

    public LoanApplicationResponse createApplication(UUID customerId, CreateLoanApplicationRequest request) {
        LoanProduct product = loanProductRepository.findByProductCodeAndIsActiveTrue(request.productCode())
                .orElseThrow(() -> new ProductValidationException("Product not found or inactive: " + request.productCode()));

        if (request.requestedAmountCents() < product.getMinAmountCents() || request.requestedAmountCents() > product.getMaxAmountCents()) {
            throw new ProductValidationException(
                    "Requested amount " + request.requestedAmountCents() + " cents is outside product limits [" +
                            product.getMinAmountCents() + ", " + product.getMaxAmountCents() + "]"
            );
        }

        if (request.requestedTenureMonths() < product.getMinTenureMonths() || request.requestedTenureMonths() > product.getMaxTenureMonths()) {
            throw new ProductValidationException(
                    "Requested tenure " + request.requestedTenureMonths() + " months is outside product limits [" +
                            product.getMinTenureMonths() + ", " + product.getMaxTenureMonths() + "]"
            );
        }

        CustomerDto customer = customerClient.getCustomer(customerId);
        if (customer == null) {
            throw new ResourceNotFoundException("Customer not found: " + customerId);
        }
        if (!"VERIFIED".equalsIgnoreCase(customer.kycStatus())) {
            throw new ProductValidationException("Customer KYC status must be VERIFIED (current: " + customer.kycStatus() + ")");
        }
        if ("HIGH".equalsIgnoreCase(customer.amlRiskRating())) {
            throw new ProductValidationException("Customer AML risk rating is HIGH; loan applications are blocked");
        }

        LoanApplication application = new LoanApplication();
        application.setCustomerId(customerId);
        application.setProduct(product);
        application.setRequestedAmountCents(request.requestedAmountCents());
        application.setRequestedTenureMonths(request.requestedTenureMonths());
        application.setStatus("SUBMITTED");

        application = loanApplicationRepository.save(application);
        return loanMapper.toApplicationResponse(application);
    }

    @Transactional(readOnly = true)
    public Page<LoanApplicationResponse> getApplicationsForCustomer(UUID customerId, Pageable pageable) {
        return loanApplicationRepository.findByCustomerId(customerId, pageable)
                .map(loanMapper::toApplicationResponse);
    }

    @Transactional(readOnly = true)
    public Page<LoanApplicationResponse> getApplicationsForStaff(String status, String productCode, Pageable pageable) {
        Specification<LoanApplication> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (productCode != null && !productCode.isBlank()) {
                predicates.add(cb.equal(root.get("product").get("productCode"), productCode));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return loanApplicationRepository.findAll(spec, pageable)
                .map(loanMapper::toApplicationResponse);
    }

    @Transactional(readOnly = true)
    public LoanApplicationResponse getApplication(UUID applicationId, UUID callerId, boolean isStaff) {
        LoanApplication application = loanApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Loan application not found: " + applicationId));

        if (!isStaff && !application.getCustomerId().equals(callerId)) {
            throw new ForbiddenException("Access denied to loan application");
        }

        List<LoanRepayment> repayments = List.of();
        if ("DISBURSED".equalsIgnoreCase(application.getStatus()) || "CLOSED".equalsIgnoreCase(application.getStatus())) {
            repayments = loanRepaymentRepository.findByApplicationIdOrderByDueDateAsc(applicationId);
        }
        return loanMapper.toApplicationResponse(application, repayments);
    }

    public LoanApplicationResponse reviewApplication(UUID applicationId, String reviewerSubject, ReviewLoanRequest request) {
        UUID staffId = null;
        if (reviewerSubject != null && !reviewerSubject.isBlank()) {
            try {
                staffId = UUID.fromString(reviewerSubject);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return reviewApplication(applicationId, staffId, request);
    }

    public LoanApplicationResponse reviewApplication(UUID applicationId, UUID staffId, ReviewLoanRequest request) {
        LoanApplication application = loanApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Loan application not found: " + applicationId));

        if (!"SUBMITTED".equalsIgnoreCase(application.getStatus()) && !"UNDER_REVIEW".equalsIgnoreCase(application.getStatus())) {
            throw new InvalidLoanStateException("Cannot review loan application in status " + application.getStatus());
        }

        String action = request.action() != null ? request.action().toUpperCase() : "";
        if ("APPROVE".equals(action)) {
            if (request.approvedAmountCents() == null || request.approvedAmountCents() <= 0) {
                throw new ProductValidationException("approvedAmountCents is required and must be positive when approving");
            }
            if (request.approvedInterestRateBps() == null || request.approvedInterestRateBps() <= 0) {
                throw new ProductValidationException("approvedInterestRateBps is required and must be positive when approving");
            }
            if (request.approvedTenureMonths() == null || request.approvedTenureMonths() <= 0) {
                throw new ProductValidationException("approvedTenureMonths is required and must be positive when approving");
            }
            application.setStatus("APPROVED");
            application.setApprovedAmountCents(request.approvedAmountCents());
            application.setApprovedInterestRateBps(request.approvedInterestRateBps());
            application.setApprovedTenureMonths(request.approvedTenureMonths());
            application.setRejectionReason(null);
        } else if ("REJECT".equals(action)) {
            if (request.rejectionReason() == null || request.rejectionReason().isBlank()) {
                throw new ProductValidationException("rejectionReason is required when rejecting");
            }
            application.setStatus("REJECTED");
            application.setRejectionReason(request.rejectionReason());
        } else {
            throw new ProductValidationException("Invalid review action: " + request.action() + ". Allowed: APPROVE, REJECT");
        }

        application.setReviewedBy(staffId);
        application.setReviewedAt(OffsetDateTime.now());
        application = loanApplicationRepository.save(application);

        loanEventPublisher.publishLoanReviewed(application);
        return loanMapper.toApplicationResponse(application);
    }
}
