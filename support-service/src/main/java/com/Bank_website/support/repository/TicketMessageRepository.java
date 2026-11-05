package com.Bank_website.support.repository;

import com.Bank_website.support.entity.TicketMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/** Append-only: only inserts and reads. Never add update or delete methods here. */
@Repository
public interface TicketMessageRepository extends JpaRepository<TicketMessage, UUID> {

    List<TicketMessage> findByTicketIdOrderByCreatedAtAsc(UUID ticketId);
}
