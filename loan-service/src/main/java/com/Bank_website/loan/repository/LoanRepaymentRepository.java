package com.Bank_website.loan.repository;

import com.Bank_website.loan.entity.LoanRepayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface LoanRepaymentRepository extends JpaRepository<LoanRepayment, UUID> {

    @Query("SELECT r FROM LoanRepayment r WHERE r.application.applicationId = :applicationId ORDER BY r.dueDate ASC")
    List<LoanRepayment> findByApplicationIdOrderByDueDateAsc(@Param("applicationId") UUID applicationId);

    List<LoanRepayment> findByStatusAndDueDateBefore(String status, LocalDate dueDate);
}
