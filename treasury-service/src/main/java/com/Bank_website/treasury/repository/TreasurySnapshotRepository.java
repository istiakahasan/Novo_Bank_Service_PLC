package com.Bank_website.treasury.repository;

import com.Bank_website.treasury.entity.TreasurySnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TreasurySnapshotRepository extends JpaRepository<TreasurySnapshot, UUID> {

    Optional<TreasurySnapshot> findBySnapshotDate(LocalDate snapshotDate);

    List<TreasurySnapshot> findBySnapshotDateBetweenOrderBySnapshotDateAsc(LocalDate from, LocalDate to);

    List<TreasurySnapshot> findBySnapshotDateGreaterThanEqualOrderBySnapshotDateAsc(LocalDate from);

    List<TreasurySnapshot> findBySnapshotDateLessThanEqualOrderBySnapshotDateAsc(LocalDate to);

    List<TreasurySnapshot> findAllByOrderBySnapshotDateAsc();

    @Query("SELECT s FROM TreasurySnapshot s LEFT JOIN FETCH s.gapBuckets WHERE s.snapshotId = :id")
    Optional<TreasurySnapshot> findByIdWithBuckets(@Param("id") UUID id);
}
