package com.Bank_website.rails.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "external_payments")
public class ExternalPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "payment_id", updatable = false, nullable = false)
    private UUID paymentId;

    @Column(name = "direction", nullable = false, length = 10)
    private String direction;

    @Column(name = "rail", nullable = false, length = 20)
    private String rail;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "internal_account_id")
    private UUID internalAccountId;

    @Column(name = "external_account_number", length = 50)
    private String externalAccountNumber;

    @Column(name = "external_routing_number", length = 20)
    private String externalRoutingNumber;

    @Column(name = "external_bank_name", length = 100)
    private String externalBankName;

    @Column(name = "amount_cents", nullable = false)
    private Long amountCents;

    @Column(name = "currency", length = 3)
    private String currency = "USD";

    @Column(name = "status", nullable = false, length = 20)
    private String status = "PENDING";

    @Column(name = "ledger_transaction_id")
    private UUID ledgerTransactionId;

    @Column(name = "failure_reason", length = 200)
    private String failureReason;

    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "idempotency_key", unique = true, length = 100)
    private String idempotencyKey;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    public ExternalPayment() {
    }

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
        if (this.currency == null) {
            this.currency = "USD";
        }
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(UUID paymentId) {
        this.paymentId = paymentId;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public String getRail() {
        return rail;
    }

    public void setRail(String rail) {
        this.rail = rail;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public void setCustomerId(UUID customerId) {
        this.customerId = customerId;
    }

    public UUID getInternalAccountId() {
        return internalAccountId;
    }

    public void setInternalAccountId(UUID internalAccountId) {
        this.internalAccountId = internalAccountId;
    }

    public String getExternalAccountNumber() {
        return externalAccountNumber;
    }

    public void setExternalAccountNumber(String externalAccountNumber) {
        this.externalAccountNumber = externalAccountNumber;
    }

    public String getExternalRoutingNumber() {
        return externalRoutingNumber;
    }

    public void setExternalRoutingNumber(String externalRoutingNumber) {
        this.externalRoutingNumber = externalRoutingNumber;
    }

    public String getExternalBankName() {
        return externalBankName;
    }

    public void setExternalBankName(String externalBankName) {
        this.externalBankName = externalBankName;
    }

    public Long getAmountCents() {
        return amountCents;
    }

    public void setAmountCents(Long amountCents) {
        this.amountCents = amountCents;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public UUID getLedgerTransactionId() {
        return ledgerTransactionId;
    }

    public void setLedgerTransactionId(UUID ledgerTransactionId) {
        this.ledgerTransactionId = ledgerTransactionId;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public Instant getScheduledAt() {
        return scheduledAt;
    }

    public void setScheduledAt(Instant scheduledAt) {
        this.scheduledAt = scheduledAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
