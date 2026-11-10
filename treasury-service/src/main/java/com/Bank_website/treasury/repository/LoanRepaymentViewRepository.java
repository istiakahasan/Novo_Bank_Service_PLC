package com.Bank_website.treasury.repository;

import com.Bank_website.treasury.entity.LoanRepaymentView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Repository
public interface LoanRepaymentViewRepository extends JpaRepository<LoanRepaymentView, UUID> {

    @Query("SELECT COALESCE(SUM(r.interestDueCents), 0) FROM LoanRepaymentView r WHERE r.status = 'PAID' AND r.paidAt >= :since")
    Long sumPaidInterestSince(@Param("since") OffsetDateTime since);

    @Query("SELECT COALESCE(SUM(r.principalDueCents), 0) FROM LoanRepaymentView r WHERE r.status != 'PAID' AND r.dueDate < :end")
    Long sumPrincipalDueBefore(@Param("end") LocalDate end);

    @Query("SELECT COALESCE(SUM(r.principalDueCents), 0) FROM LoanRepaymentView r WHERE r.status != 'PAID' AND r.dueDate >= :start AND r.dueDate < :end")
    Long sumPrincipalDueBetween(@Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query("SELECT COALESCE(SUM(r.principalDueCents), 0) FROM LoanRepaymentView r WHERE r.status != 'PAID' AND r.dueDate >= :start")
    Long sumPrincipalDueFrom(@Param("start") LocalDate start);
}
