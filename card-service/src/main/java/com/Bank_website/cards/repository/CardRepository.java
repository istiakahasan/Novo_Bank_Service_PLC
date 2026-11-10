package com.Bank_website.cards.repository;

import com.Bank_website.cards.entity.Card;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CardRepository extends JpaRepository<Card, UUID> {

    Optional<Card> findByCardNumberHash(String cardNumberHash);

    List<Card> findByCustomerId(UUID customerId);
}
