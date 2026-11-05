package com.Bank_website.Novo_Bank_Service_PLC.ledger.repository;

import com.Bank_website.Novo_Bank_Service_PLC.ledger.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {

    List<OutboxEvent> findByPublishedFalseOrderByCreatedAtAsc();
}
