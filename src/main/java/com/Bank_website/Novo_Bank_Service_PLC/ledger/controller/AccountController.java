package com.Bank_website.Novo_Bank_Service_PLC.ledger.controller;

import com.Bank_website.Novo_Bank_Service_PLC.ledger.dto.AccountAggregateSummaryResponse;
import com.Bank_website.Novo_Bank_Service_PLC.ledger.dto.AccountResponse;
import com.Bank_website.Novo_Bank_Service_PLC.ledger.dto.BalanceResponse;
import com.Bank_website.Novo_Bank_Service_PLC.ledger.dto.CreateAccountRequest;
import com.Bank_website.Novo_Bank_Service_PLC.ledger.dto.LedgerEntryResponse;
import com.Bank_website.Novo_Bank_Service_PLC.ledger.repository.AccountRepository;
import com.Bank_website.Novo_Bank_Service_PLC.ledger.service.AccountService;
import com.Bank_website.Novo_Bank_Service_PLC.ledger.service.TransferService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final TransferService transferService;
    private final AccountService accountService;
    private final AccountRepository accountRepository;

    public AccountController(TransferService transferService,
                             AccountService accountService,
                             AccountRepository accountRepository) {
        this.transferService = transferService;
        this.accountService = accountService;
        this.accountRepository = accountRepository;
    }

    @PreAuthorize("hasRole('STAFF') or (hasRole('CUSTOMER') and @access.isOwnCustomer(authentication, #request.customerId()))")
    @PostMapping
    public ResponseEntity<AccountResponse> create(@Valid @RequestBody CreateAccountRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(accountService.createAccount(request));
    }

    @PreAuthorize("hasAnyRole('STAFF','SYSTEM')")
    @GetMapping("/summary")
    public ResponseEntity<AccountAggregateSummaryResponse> summary() {
        List<String> types = List.of("savings", "checking");
        Long totalDeposits = accountRepository.sumBalanceByAccountTypeIn(types);
        Long accountCount = accountRepository.countByAccountTypeIn(types);
        return ResponseEntity.ok(new AccountAggregateSummaryResponse(
                totalDeposits != null ? totalDeposits : 0L,
                accountCount != null ? accountCount : 0L
        ));
    }

    @PreAuthorize("hasRole('STAFF') or (hasRole('CUSTOMER') and @access.ownsAccount(authentication, #id))")
    @GetMapping("/{id}/balance")
    public ResponseEntity<BalanceResponse> balance(@PathVariable UUID id) {
        return ResponseEntity.ok(transferService.getBalance(id));
    }

    @PreAuthorize("hasRole('STAFF') or (hasRole('CUSTOMER') and @access.ownsAccount(authentication, #id))")
    @GetMapping("/{id}/entries")
    public ResponseEntity<List<LedgerEntryResponse>> entries(@PathVariable UUID id,
                                                             @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(transferService.getEntries(id, limit));
    }
}
