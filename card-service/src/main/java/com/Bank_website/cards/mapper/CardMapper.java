package com.Bank_website.cards.mapper;

import com.Bank_website.cards.dto.CardResponse;
import com.Bank_website.cards.entity.Card;
import org.springframework.stereotype.Component;

@Component
public class CardMapper {

    public CardResponse toResponse(Card card) {
        if (card == null) {
            return null;
        }
        return new CardResponse(
                card.getCardId(),
                card.getCustomerId(),
                card.getAccountId(),
                card.getCardNumberMasked(),
                card.getExpiryMonth(),
                card.getExpiryYear(),
                card.getStatus(),
                card.getDailyLimitCents(),
                card.getCreatedAt()
        );
    }
}
