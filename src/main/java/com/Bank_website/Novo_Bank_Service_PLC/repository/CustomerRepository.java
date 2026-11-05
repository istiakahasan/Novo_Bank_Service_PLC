package com.Bank_website.Novo_Bank_Service_PLC.repository;

import com.Bank_website.Novo_Bank_Service_PLC.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * JpaSpecificationExecutor serves GET /api/customers (optional kycStatus / status / riskTier filters + paging).
 * It is used instead of a JPQL "(:p IS NULL OR c.x = :p)" query because PostgreSQL cannot infer the type of a null
 * parameter in that pattern.
 */
@Repository
public interface CustomerRepository extends JpaRepository<Customer, UUID>, JpaSpecificationExecutor<Customer> {

    Optional<Customer> findByMobileNo(String mobileNo);

    Optional<Customer> findByIdNumber(String idNumber);

    boolean existsByMobileNo(String mobileNo);

    boolean existsByIdNumber(String idNumber);

    Optional<Customer> findByCustomerNumber(String customerNumber);
}
