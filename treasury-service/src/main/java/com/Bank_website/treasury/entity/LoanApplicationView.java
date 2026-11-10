package com.Bank_website.treasury.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.util.UUID;

@Entity
@Immutable
@Table(name = "loan_applications")
public class LoanApplicationView {

    @Id
    @Column(name = "application_id")
    private UUID applicationId;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "approved_amount_cents")
    private Long approvedAmountCents;

    public LoanApplicationView() {
    }

    public UUID getApplicationId() {
        return applicationId;
    }

    public String getStatus() {
        return status;
    }

    public Long getApprovedAmountCents() {
        return approvedAmountCents;
    }
}
