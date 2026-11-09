package com.Bank_website.loan.repository;

import com.Bank_website.loan.entity.LoanApplication;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LoanApplicationRepository extends JpaRepository<LoanApplication, UUID>, JpaSpecificationExecutor<LoanApplication> {

    List<LoanApplication> findByCustomerId(UUID customerId);

    Page<LoanApplication> findByCustomerId(UUID customerId, Pageable pageable);

    List<LoanApplication> findByStatus(String status);

    Page<LoanApplication> findByStatus(String status, Pageable pageable);

    List<LoanApplication> findByCustomerIdAndStatus(UUID customerId, String status);

    Page<LoanApplication> findByCustomerIdAndStatus(UUID customerId, String status, Pageable pageable);
}
