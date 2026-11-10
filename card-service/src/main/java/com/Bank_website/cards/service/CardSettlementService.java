package com.Bank_website.cards.service;

import com.Bank_website.cards.dto.TransferRequestDto;
import com.Bank_website.cards.dto.TransferResponseDto;
import com.Bank_website.cards.entity.Card;
import com.Bank_website.cards.entity.CardTransaction;
import com.Bank_website.cards.repository.CardRepository;
import com.Bank_website.cards.repository.CardTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class CardSettlementService {

    private static final Logger log = LoggerFactory.getLogger(CardSettlementService.class);

    private final CardTransactionRepository cardTransactionRepository;
    private final CardRepository cardRepository;
    private final LedgerClient ledgerClient;
    private final UUID cardSettlementAccountId;
    private final UUID merchantSettlementAccountId;

    public CardSettlementService(CardTransactionRepository cardTransactionRepository,
                                 CardRepository cardRepository,
                                 LedgerClient ledgerClient,
                                 @Value("${card.settlement.account.id}") UUID cardSettlementAccountId,
                                 @Value("${merchant.settlement.account.id}") UUID merchantSettlementAccountId) {
        this.cardTransactionRepository = cardTransactionRepository;
        this.cardRepository = cardRepository;
        this.ledgerClient = ledgerClient;
        this.cardSettlementAccountId = cardSettlementAccountId;
        this.merchantSettlementAccountId = merchantSettlementAccountId;
    }

    @Transactional
    public Map<String, Object> processBatch() {
        Optional<CardTransaction> opt = cardTransactionRepository.findFirstByStageOrderByCreatedAtAsc("AUTHORIZED");
        if (opt.isEmpty()) {
            return Map.of("processed", false, "message", "No transactions pending batching");
        }

        CardTransaction txn = opt.get();
        txn.setStage("BATCHED");
        cardTransactionRepository.save(txn);
        log.info("Card transaction {} moved to BATCHED", txn.getCardTxnId());

        Map<String, Object> result = new HashMap<>();
        result.put("processed", true);
        result.put("cardTxnId", txn.getCardTxnId().toString());
        result.put("stage", "BATCHED");
        return result;
    }

    @Transactional
    public Map<String, Object> processClear() {
        Optional<CardTransaction> opt = cardTransactionRepository.findFirstByStageOrderByCreatedAtAsc("BATCHED");
        if (opt.isEmpty()) {
            return Map.of("processed", false, "message", "No transactions pending clearing");
        }

        CardTransaction txn = opt.get();
        txn.setStage("CLEARED");
        cardTransactionRepository.save(txn);
        log.info("Card transaction {} moved to CLEARED", txn.getCardTxnId());

        Map<String, Object> result = new HashMap<>();
        result.put("processed", true);
        result.put("cardTxnId", txn.getCardTxnId().toString());
        result.put("stage", "CLEARED");
        return result;
    }

    @Transactional
    public Map<String, Object> processFund() {
        Optional<CardTransaction> opt = cardTransactionRepository.findFirstByStageOrderByCreatedAtAsc("CLEARED");
        if (opt.isEmpty()) {
            return Map.of("processed", false, "message", "No transactions pending funding");
        }

        CardTransaction txn = opt.get();
        UUID cardTxnId = txn.getCardTxnId();

        try {
            TransferRequestDto fundTransfer = new TransferRequestDto(
                    cardSettlementAccountId,
                    merchantSettlementAccountId,
                    txn.getAmountCents(),
                    "card-fund-" + cardTxnId,
                    "Card funding: " + txn.getMerchantName()
            );

            TransferResponseDto fundResponse = ledgerClient.postTransfer(fundTransfer);
            txn.setLedgerTransactionId(fundResponse.transactionId());
            txn.setStage("FUNDED");
            txn.setSettledAt(Instant.now());
            cardTransactionRepository.save(txn);
            log.info("Card transaction {} funded successfully", cardTxnId);

            Map<String, Object> result = new HashMap<>();
            result.put("processed", true);
            result.put("cardTxnId", cardTxnId.toString());
            result.put("stage", "FUNDED");
            result.put("ledgerTransactionId", fundResponse.transactionId().toString());
            return result;
        } catch (Exception ex) {
            log.error("Funding failed for transaction {}. Executing compensating reversal: {}", cardTxnId, ex.getMessage());

            Optional<Card> cardOpt = cardRepository.findById(txn.getCardId());
            if (cardOpt.isPresent()) {
                Card card = cardOpt.get();
                try {
                    TransferRequestDto reversal = new TransferRequestDto(
                            cardSettlementAccountId,
                            card.getAccountId(),
                            txn.getAmountCents(),
                            "card-reverse-" + cardTxnId,
                            "Card hold reversal: " + txn.getMerchantName()
                    );
                    ledgerClient.postTransfer(reversal);
                    log.info("Compensating reversal completed for card transaction {}", cardTxnId);
                } catch (Exception revEx) {
                    log.error("Failed to execute compensating reversal for transaction {}: {}", cardTxnId, revEx.getMessage());
                }
            }

            txn.setStage("REVERSED");
            txn.setSettledAt(Instant.now());
            cardTransactionRepository.save(txn);

            Map<String, Object> result = new HashMap<>();
            result.put("processed", true);
            result.put("cardTxnId", cardTxnId.toString());
            result.put("stage", "REVERSED");
            result.put("reason", "Funding failed: " + ex.getMessage());
            return result;
        }
    }
}
