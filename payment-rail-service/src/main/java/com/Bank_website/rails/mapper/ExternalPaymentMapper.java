package com.Bank_website.rails.mapper;

import com.Bank_website.rails.dto.ExternalPaymentResponse;
import com.Bank_website.rails.entity.ExternalPayment;
import org.springframework.stereotype.Component;

@Component
public class ExternalPaymentMapper {

    public ExternalPaymentResponse toResponse(ExternalPayment payment) {
        if (payment == null) {
            return null;
        }
        return new ExternalPaymentResponse(
                payment.getPaymentId(),
                payment.getDirection(),
                payment.getRail(),
                payment.getCustomerId(),
                payment.getInternalAccountId(),
                payment.getExternalAccountNumber(),
                payment.getExternalRoutingNumber(),
                payment.getExternalBankName(),
                payment.getAmountCents(),
                payment.getCurrency(),
                payment.getStatus(),
                payment.getLedgerTransactionId(),
                payment.getFailureReason(),
                payment.getScheduledAt(),
                payment.getCompletedAt(),
                payment.getIdempotencyKey(),
                payment.getCreatedAt()
        );
    }
}
