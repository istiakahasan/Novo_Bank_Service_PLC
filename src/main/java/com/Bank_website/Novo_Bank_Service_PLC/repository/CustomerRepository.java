package com.Bank_website.Novo_Bank_Service_PLC.repository;

import com.Bank_website.Novo_Bank_Service_PLC.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    Optional<Customer> findByMobileNo(String mobileNo);

    Optional<Customer> findByIdNumber(String idNumber);

    boolean existsByMobileNo(String mobileNo);

    boolean existsByIdNumber(String idNumber);

    Optional<Customer> findByCustomerNumber(String customerNumber);

    @Query("select c from Customer c where c.id = (select a.customerId from Account a where a.id = :accountId)")
    Optional<Customer> findByAccountId(@Param("accountId") UUID accountId);
}
