package com.Bank_website.cards.controller;

import com.Bank_website.cards.dto.AuthorizeCardRequest;
import com.Bank_website.cards.dto.AuthorizeCardResponse;
import com.Bank_website.cards.dto.CardResponse;
import com.Bank_website.cards.dto.CreateCardRequest;
import com.Bank_website.cards.dto.UpdateCardStatusRequest;
import com.Bank_website.cards.service.CardAuthorizationService;
import com.Bank_website.cards.service.CardService;
import com.Bank_website.security.AuthPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/cards")
public class CardController {

    private final CardService cardService;
    private final CardAuthorizationService cardAuthorizationService;

    public CardController(CardService cardService,
                          CardAuthorizationService cardAuthorizationService) {
        this.cardService = cardService;
        this.cardAuthorizationService = cardAuthorizationService;
    }

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<CardResponse> createCard(@Valid @RequestBody CreateCardRequest request,
                                                   Authentication authentication) {
        AuthPrincipal principal = AuthPrincipal.of(authentication);
        CardResponse response = cardService.createCard(principal.customerId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('CUSTOMER', 'STAFF')")
    public ResponseEntity<List<CardResponse>> getCards(Authentication authentication) {
        AuthPrincipal principal = AuthPrincipal.of(authentication);
        List<CardResponse> response = cardService.getCards(principal);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'STAFF')")
    public ResponseEntity<CardResponse> updateStatus(@PathVariable UUID id,
                                                     @Valid @RequestBody UpdateCardStatusRequest request,
                                                     Authentication authentication) {
        AuthPrincipal principal = AuthPrincipal.of(authentication);
        CardResponse response = cardService.updateStatus(id, request, principal);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/authorize")
    @PreAuthorize("hasRole('SYSTEM')")
    public ResponseEntity<AuthorizeCardResponse> authorize(@Valid @RequestBody AuthorizeCardRequest request) {
        AuthorizeCardResponse response = cardAuthorizationService.authorize(request);
        return ResponseEntity.ok(response);
    }
}
