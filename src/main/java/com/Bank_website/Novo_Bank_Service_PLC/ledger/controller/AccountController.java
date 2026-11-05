package com.Bank_website.Novo_Bank_Service_PLC.ledger.controller;

import com.Bank_website.Novo_Bank_Service_PLC.ledger.dto.BalanceResponse;
import com.Bank_website.Novo_Bank_Service_PLC.ledger.dto.LedgerEntryResponse;
import com.Bank_website.Novo_Bank_Service_PLC.ledger.service.TransferService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final TransferService transferService;

    public AccountController(TransferService transferService) {
        this.transferService = transferService;
    }

    @GetMapping("/{id}/balance")
    public ResponseEntity<BalanceResponse> balance(@PathVariable UUID id) {
        return ResponseEntity.ok(transferService.getBalance(id));
    }

    @GetMapping("/{id}/entries")
    public ResponseEntity<List<LedgerEntryResponse>> entries(@PathVariable UUID id,
                                                             @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(transferService.getEntries(id, limit));
    }
}
