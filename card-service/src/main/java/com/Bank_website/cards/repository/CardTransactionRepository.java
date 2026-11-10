package com.Bank_website.cards.repository;

import com.Bank_website.cards.entity.CardTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CardTransactionRepository extends JpaRepository<CardTransaction, UUID> {

    Optional<CardTransaction> findFirstByStageOrderByCreatedAtAsc(String stage);

    @Query("SELECT COALESCE(SUM(t.amountCents), 0) FROM CardTransaction t " +
           "WHERE t.cardId = :cardId " +
           "AND t.stage IN ('AUTHORIZED', 'BATCHED', 'CLEARED', 'FUNDED') " +
           "AND t.createdAt >= :since")
    Long sumTodayByCardId(@Param("cardId") UUID cardId, @Param("since") Instant since);
}
