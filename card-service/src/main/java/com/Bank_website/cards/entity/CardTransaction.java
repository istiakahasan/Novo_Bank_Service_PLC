package com.Bank_website.cards.entity;

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
@Table(name = "card_transactions")
public class CardTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "card_txn_id", updatable = false, nullable = false)
    private UUID cardTxnId;

    @Column(name = "card_id", nullable = false)
    private UUID cardId;

    @Column(name = "merchant_name", nullable = false, length = 100)
    private String merchantName;

    @Column(name = "merchant_category", length = 10)
    private String merchantCategory;

    @Column(name = "amount_cents", nullable = false)
    private Long amountCents;

    @Column(name = "stage", nullable = false, length = 20)
    private String stage = "AUTHORIZED";

    @Column(name = "authorization_code", length = 10)
    private String authorizationCode;

    @Column(name = "ledger_hold_id")
    private UUID ledgerHoldId;

    @Column(name = "ledger_transaction_id")
    private UUID ledgerTransactionId;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "settled_at")
    private Instant settledAt;

    public CardTransaction() {
    }

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
        if (this.stage == null) {
            this.stage = "AUTHORIZED";
        }
    }

    public UUID getCardTxnId() {
        return cardTxnId;
    }

    public void setCardTxnId(UUID cardTxnId) {
        this.cardTxnId = cardTxnId;
    }

    public UUID getCardId() {
        return cardId;
    }

    public void setCardId(UUID cardId) {
        this.cardId = cardId;
    }

    public String getMerchantName() {
        return merchantName;
    }

    public void setMerchantName(String merchantName) {
        this.merchantName = merchantName;
    }

    public String getMerchantCategory() {
        return merchantCategory;
    }

    public void setMerchantCategory(String merchantCategory) {
        this.merchantCategory = merchantCategory;
    }

    public Long getAmountCents() {
        return amountCents;
    }

    public void setAmountCents(Long amountCents) {
        this.amountCents = amountCents;
    }

    public String getStage() {
        return stage;
    }

    public void setStage(String stage) {
        this.stage = stage;
    }

    public String getAuthorizationCode() {
        return authorizationCode;
    }

    public void setAuthorizationCode(String authorizationCode) {
        this.authorizationCode = authorizationCode;
    }

    public UUID getLedgerHoldId() {
        return ledgerHoldId;
    }

    public void setLedgerHoldId(UUID ledgerHoldId) {
        this.ledgerHoldId = ledgerHoldId;
    }

    public UUID getLedgerTransactionId() {
        return ledgerTransactionId;
    }

    public void setLedgerTransactionId(UUID ledgerTransactionId) {
        this.ledgerTransactionId = ledgerTransactionId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getSettledAt() {
        return settledAt;
    }

    public void setSettledAt(Instant settledAt) {
        this.settledAt = settledAt;
    }
}
