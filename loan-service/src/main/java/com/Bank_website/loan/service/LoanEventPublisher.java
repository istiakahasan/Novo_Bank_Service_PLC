package com.Bank_website.loan.service;

import com.Bank_website.loan.entity.LoanApplication;
import com.Bank_website.loan.entity.LoanRepayment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class LoanEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(LoanEventPublisher.class);

    private final RestClient restClient;
    private final String topicUrl;
    private final String token;

    public LoanEventPublisher(RestClient.Builder builder,
                              @Value("${qstash.topic-url:}") String topicUrl,
                              @Value("${qstash.token:}") String token) {
        this.restClient = builder.build();
        this.topicUrl = topicUrl;
        this.token = token;
    }

    public void publishLoanReviewed(LoanApplication application) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", "loan.reviewed");
        payload.put("application_id", application.getApplicationId());
        payload.put("customer_id", application.getCustomerId());
        payload.put("status", application.getStatus());
        payload.put("approved_amount_cents", application.getApprovedAmountCents());
        payload.put("approved_interest_rate_bps", application.getApprovedInterestRateBps());
        payload.put("approved_tenure_months", application.getApprovedTenureMonths());
        payload.put("rejection_reason", application.getRejectionReason());
        payload.put("reviewed_by", application.getReviewedBy());
        payload.put("reviewed_at", application.getReviewedAt() != null ? application.getReviewedAt().toString() : null);
        payload.put("occurred_at", OffsetDateTime.now().toString());
        send(payload);
    }

    public void publishLoanDisbursed(LoanApplication application) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", "loan.disbursed");
        payload.put("application_id", application.getApplicationId());
        payload.put("customer_id", application.getCustomerId());
        payload.put("product_code", application.getProduct() != null ? application.getProduct().getProductCode() : null);
        payload.put("amount_cents", application.getApprovedAmountCents());
        payload.put("interest_rate_bps", application.getApprovedInterestRateBps());
        payload.put("tenure_months", application.getApprovedTenureMonths());
        payload.put("disbursement_transaction_id", application.getDisbursementTransactionId());
        payload.put("disbursed_at", application.getDisbursedAt() != null ? application.getDisbursedAt().toString() : null);
        payload.put("occurred_at", OffsetDateTime.now().toString());
        send(payload);
    }

    public void publishRepaymentPaid(LoanRepayment repayment) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", "loan.repayment.paid");
        payload.put("repayment_id", repayment.getRepaymentId());
        payload.put("application_id", repayment.getApplication() != null ? repayment.getApplication().getApplicationId() : null);
        payload.put("customer_id", repayment.getApplication() != null ? repayment.getApplication().getCustomerId() : null);
        payload.put("amount_cents", repayment.getTotalDueCents());
        payload.put("payment_transaction_id", repayment.getPaymentTransactionId());
        payload.put("paid_at", repayment.getPaidAt() != null ? repayment.getPaidAt().toString() : null);
        payload.put("occurred_at", OffsetDateTime.now().toString());
        send(payload);
    }

    public void publishRepaymentOverdue(LoanRepayment repayment) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", "loan.repayment.overdue");
        payload.put("repayment_id", repayment.getRepaymentId());
        payload.put("application_id", repayment.getApplication() != null ? repayment.getApplication().getApplicationId() : null);
        payload.put("customer_id", repayment.getApplication() != null ? repayment.getApplication().getCustomerId() : null);
        payload.put("amount_cents", repayment.getTotalDueCents());
        payload.put("due_date", repayment.getDueDate() != null ? repayment.getDueDate().toString() : null);
        payload.put("occurred_at", OffsetDateTime.now().toString());
        send(payload);
    }

    public void publishCollectionReminder(UUID applicationId, UUID repaymentId, Long amountCents, LocalDate dueDate) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", "collection.reminder");
        payload.put("application_id", applicationId);
        payload.put("repayment_id", repaymentId);
        payload.put("amount_cents", amountCents);
        payload.put("due_date", dueDate != null ? dueDate.toString() : null);
        payload.put("occurred_at", OffsetDateTime.now().toString());
        send(payload);
    }

    private void send(Map<String, Object> payload) {
        if (topicUrl == null || topicUrl.isBlank()) {
            log.info("QStash topic URL not configured, skipping event {}", payload.get("event"));
            return;
        }
        try {
            restClient.post()
                    .uri(topicUrl)
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ex) {
            log.warn("Failed to publish {} to QStash: {}", payload.get("event"), ex.getMessage());
        }
    }
}
