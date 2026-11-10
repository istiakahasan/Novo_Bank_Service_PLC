package com.Bank_website.treasury.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "alm_gap_buckets")
public class AlmGapBucket {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "bucket_id")
    private UUID bucketId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "snapshot_id", nullable = false)
    private TreasurySnapshot snapshot;

    @Column(name = "bucket_label", nullable = false, length = 20)
    private String bucketLabel;

    @Column(name = "assets_cents", nullable = false)
    private Long assetsCents;

    @Column(name = "liabilities_cents", nullable = false)
    private Long liabilitiesCents;

    @Column(name = "gap_cents", nullable = false)
    private Long gapCents;

    public AlmGapBucket() {
    }

    public AlmGapBucket(TreasurySnapshot snapshot, String bucketLabel, Long assetsCents, Long liabilitiesCents, Long gapCents) {
        this.snapshot = snapshot;
        this.bucketLabel = bucketLabel;
        this.assetsCents = assetsCents;
        this.liabilitiesCents = liabilitiesCents;
        this.gapCents = gapCents;
    }

    public UUID getBucketId() {
        return bucketId;
    }

    public void setBucketId(UUID bucketId) {
        this.bucketId = bucketId;
    }

    public TreasurySnapshot getSnapshot() {
        return snapshot;
    }

    public void setSnapshot(TreasurySnapshot snapshot) {
        this.snapshot = snapshot;
    }

    public String getBucketLabel() {
        return bucketLabel;
    }

    public void setBucketLabel(String bucketLabel) {
        this.bucketLabel = bucketLabel;
    }

    public Long getAssetsCents() {
        return assetsCents;
    }

    public void setAssetsCents(Long assetsCents) {
        this.assetsCents = assetsCents;
    }

    public Long getLiabilitiesCents() {
        return liabilitiesCents;
    }

    public void setLiabilitiesCents(Long liabilitiesCents) {
        this.liabilitiesCents = liabilitiesCents;
    }

    public Long getGapCents() {
        return gapCents;
    }

    public void setGapCents(Long gapCents) {
        this.gapCents = gapCents;
    }
}
