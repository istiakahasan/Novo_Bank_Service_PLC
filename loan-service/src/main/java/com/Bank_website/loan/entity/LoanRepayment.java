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

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "loan_repayments")
public class LoanRepayment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "repayment_id")
    private UUID repaymentId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private LoanApplication application;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "principal_due_cents", nullable = false)
    private Long principalDueCents;

    @Column(name = "interest_due_cents", nullable = false)
    private Long interestDueCents;

    @Column(name = "total_due_cents", nullable = false)
    private Long totalDueCents;

    @Column(name = "paid_at")
    private OffsetDateTime paidAt;

    @Column(name = "payment_transaction_id")
    private UUID paymentTransactionId;

    @Column(name = "status", length = 20)
    private String status = "PENDING";

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    public LoanRepayment() {
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
        if (status == null) {
            status = "PENDING";
        }
    }

    public UUID getRepaymentId() {
        return repaymentId;
    }

    public void setRepaymentId(UUID repaymentId) {
        this.repaymentId = repaymentId;
    }

    public LoanApplication getApplication() {
        return application;
    }

    public void setApplication(LoanApplication application) {
        this.application = application;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public Long getPrincipalDueCents() {
        return principalDueCents;
    }

    public void setPrincipalDueCents(Long principalDueCents) {
        this.principalDueCents = principalDueCents;
    }

    public Long getInterestDueCents() {
        return interestDueCents;
    }

    public void setInterestDueCents(Long interestDueCents) {
        this.interestDueCents = interestDueCents;
    }

    public Long getTotalDueCents() {
        return totalDueCents;
    }

    public void setTotalDueCents(Long totalDueCents) {
        this.totalDueCents = totalDueCents;
    }

    public OffsetDateTime getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(OffsetDateTime paidAt) {
        this.paidAt = paidAt;
    }

    public UUID getPaymentTransactionId() {
        return paymentTransactionId;
    }

    public void setPaymentTransactionId(UUID paymentTransactionId) {
        this.paymentTransactionId = paymentTransactionId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
