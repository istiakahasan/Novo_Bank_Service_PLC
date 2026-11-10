package com.Bank_website.Novo_Bank_Service_PLC.ledger.repository;

import com.Bank_website.Novo_Bank_Service_PLC.ledger.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {

    Optional<Account> findByAccountNumber(String accountNumber);

    List<Account> findByCustomerId(UUID customerId);

    @Query("SELECT a.customerId FROM Account a WHERE a.id = :accountId")
    Optional<UUID> findCustomerIdByAccountId(@Param("accountId") UUID accountId);

    @Query("SELECT SUM(a.balanceCents) FROM Account a WHERE a.accountType IN :types AND a.status = 'active'")
    Long sumBalanceByAccountTypeIn(@Param("types") List<String> types);

    @Query("SELECT COUNT(a) FROM Account a WHERE a.accountType IN :types AND a.status = 'active'")
    Long countByAccountTypeIn(@Param("types") List<String> types);

    /**
     * Atomic single-statement debit. The balance guard means a concurrent debit that
     * leaves too little money yields 0 rows affected instead of a negative balance.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE accounts SET balance_cents = balance_cents - :amt, updated_at = now() "
            + "WHERE id = :id AND status = 'active' AND balance_cents >= :amt", nativeQuery = true)
    int decrementBalance(@Param("id") UUID id, @Param("amt") Long amt);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE accounts SET balance_cents = balance_cents + :amt, updated_at = now() "
            + "WHERE id = :id AND status = 'active'", nativeQuery = true)
    int incrementBalance(@Param("id") UUID id, @Param("amt") Long amt);
}
