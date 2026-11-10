package com.Bank_website.treasury.repository;

import com.Bank_website.treasury.entity.AlmGapBucket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AlmGapBucketRepository extends JpaRepository<AlmGapBucket, UUID> {

    List<AlmGapBucket> findBySnapshotSnapshotId(UUID snapshotId);
}
