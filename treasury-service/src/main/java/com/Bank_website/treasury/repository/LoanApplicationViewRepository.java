package com.Bank_website.treasury.repository;

import com.Bank_website.treasury.entity.LoanApplicationView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface LoanApplicationViewRepository extends JpaRepository<LoanApplicationView, UUID> {

    @Query("SELECT COALESCE(SUM(l.approvedAmountCents), 0) FROM LoanApplicationView l WHERE l.status = 'DISBURSED'")
    Long sumApprovedAmountByStatusDisbursed();
}
