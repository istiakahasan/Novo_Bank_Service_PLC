package com.Bank_website.cards.dto;

import java.util.UUID;

public record AuthorizeCardResponse(
        UUID cardTxnId,
        String authorizationCode,
        String stage
) {
}
