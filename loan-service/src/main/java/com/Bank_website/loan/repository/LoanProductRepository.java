package com.Bank_website.loan.repository;

import com.Bank_website.loan.entity.LoanProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LoanProductRepository extends JpaRepository<LoanProduct, UUID> {

    Optional<LoanProduct> findByProductCodeAndIsActiveTrue(String productCode);

    List<LoanProduct> findByIsActiveTrue();
}
