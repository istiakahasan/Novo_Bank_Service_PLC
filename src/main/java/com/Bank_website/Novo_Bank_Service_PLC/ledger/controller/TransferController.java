package com.Bank_website.Novo_Bank_Service_PLC.ledger.controller;

import com.Bank_website.Novo_Bank_Service_PLC.ledger.dto.TransferRequest;
import com.Bank_website.Novo_Bank_Service_PLC.ledger.dto.TransferResponse;
import com.Bank_website.Novo_Bank_Service_PLC.ledger.service.TransferService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/transfers")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    public ResponseEntity<TransferResponse> transfer(@Valid @RequestBody TransferRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(transferService.executeTransfer(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TransferResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(transferService.getTransfer(id));
    }
}
