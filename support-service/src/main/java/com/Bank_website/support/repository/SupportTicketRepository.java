package com.Bank_website.support.repository;

import com.Bank_website.support.entity.SupportTicket;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/** JpaSpecificationExecutor is used for the STAFF filters: optional UUID parameters in JPQL break on PostgreSQL. */
@Repository
public interface SupportTicketRepository extends JpaRepository<SupportTicket, UUID>, JpaSpecificationExecutor<SupportTicket> {

    Page<SupportTicket> findByCustomerId(UUID customerId, Pageable pageable);

    List<SupportTicket> findByStatus(String status);

    List<SupportTicket> findByAssignedAgentId(UUID assignedAgentId);

    boolean existsByRelatedTransactionId(UUID relatedTransactionId);
}
