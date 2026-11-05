package com.Bank_website.Novo_Bank_Service_PLC.ledger.controller;

import com.Bank_website.Novo_Bank_Service_PLC.ledger.dto.AccountSummaryResponse;
import com.Bank_website.Novo_Bank_Service_PLC.ledger.entity.Account;
import com.Bank_website.Novo_Bank_Service_PLC.ledger.mapper.TransferMapper;
import com.Bank_website.Novo_Bank_Service_PLC.ledger.repository.AccountRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Service-to-service lookup used by the Customer Service: all accounts of one customer.
 * SYSTEM only (X-Service-Id / X-Service-Key); users go through /api/customers/{id}/accounts instead.
 */
@RestController
@RequestMapping("/api/accounts")
public class LedgerAccountController {

    private final AccountRepository accountRepository;
    private final TransferMapper mapper;

    public LedgerAccountController(AccountRepository accountRepository, TransferMapper mapper) {
        this.accountRepository = accountRepository;
        this.mapper = mapper;
    }

    @PreAuthorize("hasRole('SYSTEM')")
    @GetMapping(params = "customerId")
    public ResponseEntity<List<AccountSummaryResponse>> byCustomer(@RequestParam UUID customerId) {
        List<AccountSummaryResponse> accounts = accountRepository.findByCustomerId(customerId).stream()
                .sorted(Comparator.comparing(Account::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(mapper::toAccountSummary)
                .toList();
        return ResponseEntity.ok(accounts);
    }
}
