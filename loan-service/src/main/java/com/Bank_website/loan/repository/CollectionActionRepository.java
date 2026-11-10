package com.Bank_website.loan.repository;

import com.Bank_website.loan.entity.CollectionAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CollectionActionRepository extends JpaRepository<CollectionAction, UUID> {

    @Query("SELECT a FROM CollectionAction a WHERE a.application.applicationId = :applicationId ORDER BY a.createdAt DESC")
    List<CollectionAction> findByApplicationIdOrderByCreatedAtDesc(@Param("applicationId") UUID applicationId);

    @Query("SELECT a FROM CollectionAction a WHERE a.application.applicationId = :applicationId ORDER BY a.createdAt DESC LIMIT 1")
    Optional<CollectionAction> findFirstByApplicationIdOrderByCreatedAtDesc(@Param("applicationId") UUID applicationId);

    @Query("SELECT COUNT(a) > 0 FROM CollectionAction a WHERE a.application.applicationId = :applicationId AND a.actionType IN ('DEMAND_LETTER', 'LEGAL_NOTICE') AND a.createdAt >= :since")
    boolean hasRecentFormalAction(@Param("applicationId") UUID applicationId, @Param("since") OffsetDateTime since);
}
