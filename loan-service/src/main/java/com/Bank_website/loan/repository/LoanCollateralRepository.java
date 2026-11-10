package com.Bank_website.loan.repository;

import com.Bank_website.loan.entity.LoanCollateral;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LoanCollateralRepository extends JpaRepository<LoanCollateral, UUID> {

    @Query("SELECT c FROM LoanCollateral c WHERE c.application.applicationId = :applicationId ORDER BY c.createdAt DESC")
    List<LoanCollateral> findByApplicationId(@Param("applicationId") UUID applicationId);
}
