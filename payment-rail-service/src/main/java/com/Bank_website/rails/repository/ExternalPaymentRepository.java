package com.Bank_website.rails.repository;

import com.Bank_website.rails.entity.ExternalPayment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExternalPaymentRepository extends JpaRepository<ExternalPayment, UUID> {

    Optional<ExternalPayment> findByIdempotencyKey(String idempotencyKey);

    Optional<ExternalPayment> findFirstByDirectionAndStatusOrderByCreatedAtAsc(String direction, String status);

    Page<ExternalPayment> findByCustomerId(UUID customerId, Pageable pageable);

    Page<ExternalPayment> findByDirectionAndStatus(String direction, String status, Pageable pageable);
}
