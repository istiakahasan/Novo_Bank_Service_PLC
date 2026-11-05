package com.Bank_website.fraud.repository;

import com.Bank_website.fraud.entity.FraudAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FraudAlertRepository extends JpaRepository<FraudAlert, UUID> {

    List<FraudAlert> findByTransactionId(UUID transactionId);

    boolean existsByTransactionIdAndRuleFired(UUID transactionId, String ruleFired);
}
