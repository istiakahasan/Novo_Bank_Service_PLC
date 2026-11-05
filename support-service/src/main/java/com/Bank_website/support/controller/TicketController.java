package com.Bank_website.support.controller;

import com.Bank_website.support.dto.CreateMessageRequest;
import com.Bank_website.support.dto.CreateTicketRequest;
import com.Bank_website.support.dto.TicketListResponse;
import com.Bank_website.support.dto.TicketMessageResponse;
import com.Bank_website.support.dto.TicketResponse;
import com.Bank_website.support.dto.UpdateTicketRequest;
import com.Bank_website.support.service.TicketService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @PostMapping
    public ResponseEntity<TicketResponse> create(@Valid @RequestBody CreateTicketRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ticketService.createTicket(request));
    }

    @PreAuthorize("hasAnyRole('CUSTOMER','STAFF')")
    @GetMapping
    public ResponseEntity<TicketListResponse> list(@RequestParam(required = false) String status,
                                                   @RequestParam(required = false) String priority,
                                                   @RequestParam(required = false) UUID assignedAgentId,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ticketService.listTickets(status, priority, assignedAgentId, page, size));
    }

    @PreAuthorize("hasAnyRole('CUSTOMER','STAFF')")
    @GetMapping("/{id}")
    public ResponseEntity<TicketResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ticketService.getTicket(id));
    }

    @PreAuthorize("hasRole('STAFF')")
    @PatchMapping("/{id}")
    public ResponseEntity<TicketResponse> update(@PathVariable UUID id, @Valid @RequestBody UpdateTicketRequest request) {
        return ResponseEntity.ok(ticketService.updateTicket(id, request));
    }

    @PreAuthorize("hasAnyRole('CUSTOMER','STAFF')")
    @PostMapping("/{id}/messages")
    public ResponseEntity<TicketMessageResponse> addMessage(@PathVariable UUID id,
                                                            @Valid @RequestBody CreateMessageRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ticketService.addMessage(id, request));
    }
}
