package com.Bank_website.treasury.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Immutable
@Table(name = "loan_repayments")
public class LoanRepaymentView {

    @Id
    @Column(name = "repayment_id")
    private UUID repaymentId;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "principal_due_cents", nullable = false)
    private Long principalDueCents;

    @Column(name = "interest_due_cents", nullable = false)
    private Long interestDueCents;

    @Column(name = "status")
    private String status;

    @Column(name = "paid_at")
    private OffsetDateTime paidAt;

    public LoanRepaymentView() {
    }

    public UUID getRepaymentId() {
        return repaymentId;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public Long getPrincipalDueCents() {
        return principalDueCents;
    }

    public Long getInterestDueCents() {
        return interestDueCents;
    }

    public String getStatus() {
        return status;
    }

    public OffsetDateTime getPaidAt() {
        return paidAt;
    }
}
