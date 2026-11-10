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
@Table(name = "loan_collateral")
public class LoanCollateral {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "collateral_id")
    private UUID collateralId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private LoanApplication application;

    @Column(name = "collateral_type", nullable = false, length = 30)
    private String collateralType;

    @Column(name = "description", nullable = false, length = 500)
    private String description;

    @Column(name = "estimated_value_cents", nullable = false)
    private Long estimatedValueCents;

    @Column(name = "valuation_date", nullable = false)
    private LocalDate valuationDate;

    @Column(name = "status", length = 20)
    private String status = "PLEDGED";

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    public LoanCollateral() {
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
        if (status == null) {
            status = "PLEDGED";
        }
    }

    public UUID getCollateralId() {
        return collateralId;
    }

    public void setCollateralId(UUID collateralId) {
        this.collateralId = collateralId;
    }

    public LoanApplication getApplication() {
        return application;
    }

    public void setApplication(LoanApplication application) {
        this.application = application;
    }

    public String getCollateralType() {
        return collateralType;
    }

    public void setCollateralType(String collateralType) {
        this.collateralType = collateralType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Long getEstimatedValueCents() {
        return estimatedValueCents;
    }

    public void setEstimatedValueCents(Long estimatedValueCents) {
        this.estimatedValueCents = estimatedValueCents;
    }

    public LocalDate getValuationDate() {
        return valuationDate;
    }

    public void setValuationDate(LocalDate valuationDate) {
        this.valuationDate = valuationDate;
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
