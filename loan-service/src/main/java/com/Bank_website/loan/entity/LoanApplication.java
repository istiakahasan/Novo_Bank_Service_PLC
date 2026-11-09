package com.Bank_website.loan.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "loan_applications")
public class LoanApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "application_id")
    private UUID applicationId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private LoanProduct product;

    @Column(name = "requested_amount_cents", nullable = false)
    private Long requestedAmountCents;

    @Column(name = "requested_tenure_months", nullable = false)
    private Integer requestedTenureMonths;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "SUBMITTED";

    @Column(name = "approved_amount_cents")
    private Long approvedAmountCents;

    @Column(name = "approved_interest_rate_bps")
    private Integer approvedInterestRateBps;

    @Column(name = "approved_tenure_months")
    private Integer approvedTenureMonths;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    @Column(name = "disbursed_at")
    private OffsetDateTime disbursedAt;

    @Column(name = "disbursement_transaction_id")
    private UUID disbursementTransactionId;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    public LoanApplication() {
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
        if (status == null) {
            status = "SUBMITTED";
        }
    }

    public UUID getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(UUID applicationId) {
        this.applicationId = applicationId;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public void setCustomerId(UUID customerId) {
        this.customerId = customerId;
    }

    public LoanProduct getProduct() {
        return product;
    }

    public void setProduct(LoanProduct product) {
        this.product = product;
    }

    public Long getRequestedAmountCents() {
        return requestedAmountCents;
    }

    public void setRequestedAmountCents(Long requestedAmountCents) {
        this.requestedAmountCents = requestedAmountCents;
    }

    public Integer getRequestedTenureMonths() {
        return requestedTenureMonths;
    }

    public void setRequestedTenureMonths(Integer requestedTenureMonths) {
        this.requestedTenureMonths = requestedTenureMonths;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getApprovedAmountCents() {
        return approvedAmountCents;
    }

    public void setApprovedAmountCents(Long approvedAmountCents) {
        this.approvedAmountCents = approvedAmountCents;
    }

    public Integer getApprovedInterestRateBps() {
        return approvedInterestRateBps;
    }

    public void setApprovedInterestRateBps(Integer approvedInterestRateBps) {
        this.approvedInterestRateBps = approvedInterestRateBps;
    }

    public Integer getApprovedTenureMonths() {
        return approvedTenureMonths;
    }

    public void setApprovedTenureMonths(Integer approvedTenureMonths) {
        this.approvedTenureMonths = approvedTenureMonths;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public UUID getReviewedBy() {
        return reviewedBy;
    }

    public void setReviewedBy(UUID reviewedBy) {
        this.reviewedBy = reviewedBy;
    }

    public OffsetDateTime getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(OffsetDateTime reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public OffsetDateTime getDisbursedAt() {
        return disbursedAt;
    }

    public void setDisbursedAt(OffsetDateTime disbursedAt) {
        this.disbursedAt = disbursedAt;
    }

    public UUID getDisbursementTransactionId() {
        return disbursementTransactionId;
    }

    public void setDisbursementTransactionId(UUID disbursementTransactionId) {
        this.disbursementTransactionId = disbursementTransactionId;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
