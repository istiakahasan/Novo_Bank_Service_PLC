package com.Bank_website.reporting.repository;

import com.Bank_website.reporting.entity.ReportJob;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReportJobRepository extends JpaRepository<ReportJob, UUID> {

    Optional<ReportJob> findFirstByStatusOrderByCreatedAtAsc(String status);

    List<ReportJob> findByCustomerId(UUID customerId);

    Page<ReportJob> findByCustomerIdOrderByCreatedAtDesc(UUID customerId, Pageable pageable);

    Page<ReportJob> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
