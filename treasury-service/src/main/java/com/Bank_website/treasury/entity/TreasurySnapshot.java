package com.Bank_website.treasury.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "treasury_snapshots")
public class TreasurySnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "snapshot_id")
    private UUID snapshotId;

    @Column(name = "snapshot_date", nullable = false, unique = true)
    private LocalDate snapshotDate;

    @Column(name = "total_deposits_cents", nullable = false)
    private Long totalDepositsCents;

    @Column(name = "total_loans_cents", nullable = false)
    private Long totalLoansCents;

    @Column(name = "liquid_assets_cents", nullable = false)
    private Long liquidAssetsCents;

    @Column(name = "lcr_ratio", precision = 6, scale = 4)
    private BigDecimal lcrRatio;

    @Column(name = "nim_bps")
    private Integer nimBps;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @OneToMany(mappedBy = "snapshot", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AlmGapBucket> gapBuckets = new ArrayList<>();

    public TreasurySnapshot() {
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    public UUID getSnapshotId() {
        return snapshotId;
    }

    public void setSnapshotId(UUID snapshotId) {
        this.snapshotId = snapshotId;
    }

    public LocalDate getSnapshotDate() {
        return snapshotDate;
    }

    public void setSnapshotDate(LocalDate snapshotDate) {
        this.snapshotDate = snapshotDate;
    }

    public Long getTotalDepositsCents() {
        return totalDepositsCents;
    }

    public void setTotalDepositsCents(Long totalDepositsCents) {
        this.totalDepositsCents = totalDepositsCents;
    }

    public Long getTotalLoansCents() {
        return totalLoansCents;
    }

    public void setTotalLoansCents(Long totalLoansCents) {
        this.totalLoansCents = totalLoansCents;
    }

    public Long getLiquidAssetsCents() {
        return liquidAssetsCents;
    }

    public void setLiquidAssetsCents(Long liquidAssetsCents) {
        this.liquidAssetsCents = liquidAssetsCents;
    }

    public BigDecimal getLcrRatio() {
        return lcrRatio;
    }

    public void setLcrRatio(BigDecimal lcrRatio) {
        this.lcrRatio = lcrRatio;
    }

    public Integer getNimBps() {
        return nimBps;
    }

    public void setNimBps(Integer nimBps) {
        this.nimBps = nimBps;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public List<AlmGapBucket> getGapBuckets() {
        return gapBuckets;
    }

    public void setGapBuckets(List<AlmGapBucket> gapBuckets) {
        this.gapBuckets = gapBuckets;
    }

    public void addGapBucket(AlmGapBucket bucket) {
        gapBuckets.add(bucket);
        bucket.setSnapshot(this);
    }
}
