package com.Bank_website.cards.service;

import com.Bank_website.cards.dto.AccountSummaryDto;
import com.Bank_website.cards.dto.CardResponse;
import com.Bank_website.cards.dto.CreateCardRequest;
import com.Bank_website.cards.dto.UpdateCardStatusRequest;
import com.Bank_website.cards.entity.Card;
import com.Bank_website.cards.exception.ForbiddenException;
import com.Bank_website.cards.exception.ResourceNotFoundException;
import com.Bank_website.cards.mapper.CardMapper;
import com.Bank_website.cards.repository.CardRepository;
import com.Bank_website.cards.util.CardNumberGenerator;
import com.Bank_website.security.AuthPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class CardService {

    private final CardRepository cardRepository;
    private final CustomerClient customerClient;
    private final CardMapper cardMapper;

    public CardService(CardRepository cardRepository,
                       CustomerClient customerClient,
                       CardMapper cardMapper) {
        this.cardRepository = cardRepository;
        this.customerClient = customerClient;
        this.cardMapper = cardMapper;
    }

    @Transactional
    public CardResponse createCard(UUID customerId, CreateCardRequest request) {
        List<AccountSummaryDto> accounts = customerClient.getAccounts(customerId);
        boolean ownsAccount = accounts.stream()
                .anyMatch(a -> a.accountId().equals(request.accountId()));

        if (!ownsAccount) {
            throw new ForbiddenException("Account " + request.accountId() + " does not belong to customer " + customerId);
        }

        String cardNumber = CardNumberGenerator.generateLuhnCardNumber();
        String cvv = String.format("%03d", ThreadLocalRandom.current().nextInt(100, 1000));

        LocalDate now = LocalDate.now();
        int expiryMonth = now.getMonthValue();
        int expiryYear = now.getYear() + 4;

        Card card = new Card();
        card.setCustomerId(customerId);
        card.setAccountId(request.accountId());
        card.setCardNumberMasked(CardNumberGenerator.mask(cardNumber));
        card.setCardNumberHash(CardNumberGenerator.sha256(cardNumber));
        card.setExpiryMonth(expiryMonth);
        card.setExpiryYear(expiryYear);
        card.setCvvHash(CardNumberGenerator.sha256(cvv));
        card.setStatus("ACTIVE");
        card.setDailyLimitCents(500000L);

        card = cardRepository.save(card);
        return cardMapper.toResponse(card);
    }

    @Transactional(readOnly = true)
    public List<CardResponse> getCards(AuthPrincipal principal) {
        List<Card> cards;
        if (principal.hasRole("STAFF")) {
            cards = cardRepository.findAll();
        } else {
            cards = cardRepository.findByCustomerId(principal.customerId());
        }
        return cards.stream().map(cardMapper::toResponse).toList();
    }

    @Transactional
    public CardResponse updateStatus(UUID cardId, UpdateCardStatusRequest request, AuthPrincipal principal) {
        Card card = cardRepository.findById(cardId)
                .orElseThrow(() -> new ResourceNotFoundException("Card not found: " + cardId));

        if (!principal.hasRole("STAFF")) {
            if (!card.getCustomerId().equals(principal.customerId())) {
                throw new ForbiddenException("Access denied to card: " + cardId);
            }
        }

        if ("CANCELLED".equals(card.getStatus())) {
            throw new IllegalStateException("Card is CANCELLED and cannot be modified");
        }

        String targetStatus = request.status().toUpperCase();
        if (!principal.hasRole("STAFF")) {
            if (!"ACTIVE".equals(targetStatus) && !"FROZEN".equals(targetStatus)) {
                throw new ForbiddenException("Customers may only transition cards between ACTIVE and FROZEN");
            }
        } else {
            if (!"ACTIVE".equals(targetStatus) && !"FROZEN".equals(targetStatus) && !"CANCELLED".equals(targetStatus)) {
                throw new IllegalArgumentException("Invalid status: " + request.status());
            }
        }

        card.setStatus(targetStatus);
        card = cardRepository.save(card);
        return cardMapper.toResponse(card);
    }
}
