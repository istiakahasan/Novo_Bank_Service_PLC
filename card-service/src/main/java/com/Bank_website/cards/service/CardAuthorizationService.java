package com.Bank_website.cards.service;

import com.Bank_website.cards.dto.AuthorizeCardRequest;
import com.Bank_website.cards.dto.AuthorizeCardResponse;
import com.Bank_website.cards.dto.TransferRequestDto;
import com.Bank_website.cards.dto.TransferResponseDto;
import com.Bank_website.cards.entity.Card;
import com.Bank_website.cards.entity.CardTransaction;
import com.Bank_website.cards.repository.CardRepository;
import com.Bank_website.cards.repository.CardTransactionRepository;
import com.Bank_website.cards.util.CardNumberGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class CardAuthorizationService {

    private static final Logger log = LoggerFactory.getLogger(CardAuthorizationService.class);

    private final CardRepository cardRepository;
    private final CardTransactionRepository cardTransactionRepository;
    private final FraudClient fraudClient;
    private final LedgerClient ledgerClient;
    private final UUID cardSettlementAccountId;

    public CardAuthorizationService(CardRepository cardRepository,
                                    CardTransactionRepository cardTransactionRepository,
                                    FraudClient fraudClient,
                                    LedgerClient ledgerClient,
                                    @Value("${card.settlement.account.id}") UUID cardSettlementAccountId) {
        this.cardRepository = cardRepository;
        this.cardTransactionRepository = cardTransactionRepository;
        this.fraudClient = fraudClient;
        this.ledgerClient = ledgerClient;
        this.cardSettlementAccountId = cardSettlementAccountId;
    }

    @Transactional
    public AuthorizeCardResponse authorize(AuthorizeCardRequest request) {
        String cardHash = CardNumberGenerator.sha256(request.cardNumber());
        Optional<Card> cardOpt = cardRepository.findByCardNumberHash(cardHash);

        if (cardOpt.isEmpty()) {
            log.warn("Card authorization rejected: card number not found");
            return new AuthorizeCardResponse(null, null, "DECLINED");
        }

        Card card = cardOpt.get();
        UUID cardId = card.getCardId();
        String cvvHash = CardNumberGenerator.sha256(request.cvv());

        // Validate CVV
        if (!cvvHash.equals(card.getCvvHash())) {
            log.warn("Card authorization rejected for card {}: invalid CVV", cardId);
            return recordDeclined(cardId, request, "INVALID_CVV");
        }

        // Validate Status
        if (!"ACTIVE".equals(card.getStatus())) {
            log.warn("Card authorization rejected for card {}: card status is {}", cardId, card.getStatus());
            return recordDeclined(cardId, request, "CARD_NOT_ACTIVE");
        }

        // Check Daily Spend Limit
        var startOfToday = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
        Long spentToday = cardTransactionRepository.sumTodayByCardId(cardId, startOfToday);
        if (spentToday == null) {
            spentToday = 0L;
        }

        if (spentToday + request.amountCents() > card.getDailyLimitCents()) {
            log.warn("Card authorization rejected for card {}: daily limit exceeded (spentToday={}, amount={}, limit={})",
                    cardId, spentToday, request.amountCents(), card.getDailyLimitCents());
            return recordDeclined(cardId, request, "DAILY_LIMIT_EXCEEDED");
        }

        // Fraud check
        boolean flagged = fraudClient.isFlagged(card.getAccountId(), request.amountCents(), cardSettlementAccountId);
        if (flagged) {
            log.warn("Card authorization rejected for card {}: fraud flagged", cardId);
            return recordDeclined(cardId, request, "FRAUD_FLAGGED");
        }

        // Create transaction row
        CardTransaction txn = new CardTransaction();
        txn.setCardId(cardId);
        txn.setMerchantName(request.merchantName());
        txn.setMerchantCategory(request.merchantCategory());
        txn.setAmountCents(request.amountCents());
        txn.setStage("AUTHORIZED");
        txn = cardTransactionRepository.save(txn);

        // Place hold via Ledger transfer to card settlement account
        TransferRequestDto holdRequest = new TransferRequestDto(
                card.getAccountId(),
                cardSettlementAccountId,
                request.amountCents(),
                "card-auth-" + txn.getCardTxnId(),
                "Card hold: " + request.merchantName()
        );

        TransferResponseDto holdResponse;
        try {
            holdResponse = ledgerClient.postTransfer(holdRequest);
        } catch (Exception ex) {
            log.warn("Card authorization hold failed in ledger for card {}: {}", cardId, ex.getMessage());
            txn.setStage("DECLINED");
            cardTransactionRepository.save(txn);
            return new AuthorizeCardResponse(txn.getCardTxnId(), null, "DECLINED");
        }

        String authCode = generateAuthCode();
        txn.setLedgerHoldId(holdResponse.transactionId());
        txn.setAuthorizationCode(authCode);
        txn = cardTransactionRepository.save(txn);

        return new AuthorizeCardResponse(txn.getCardTxnId(), authCode, "AUTHORIZED");
    }

    private AuthorizeCardResponse recordDeclined(UUID cardId, AuthorizeCardRequest request, String reason) {
        CardTransaction txn = new CardTransaction();
        txn.setCardId(cardId);
        txn.setMerchantName(request.merchantName());
        txn.setMerchantCategory(request.merchantCategory());
        txn.setAmountCents(request.amountCents());
        txn.setStage("DECLINED");
        txn = cardTransactionRepository.save(txn);
        return new AuthorizeCardResponse(txn.getCardTxnId(), null, "DECLINED");
    }

    private String generateAuthCode() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder(6);
        Random rnd = ThreadLocalRandom.current();
        for (int i = 0; i < 6; i++) {
            sb.append(chars.charAt(rnd.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
