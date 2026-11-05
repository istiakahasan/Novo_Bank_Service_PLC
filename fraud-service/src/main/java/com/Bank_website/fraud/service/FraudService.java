package com.Bank_website.fraud.service;

import com.Bank_website.fraud.dto.CustomerCreatedEvent;
import com.Bank_website.fraud.dto.CustomerScoreResult;
import com.Bank_website.fraud.dto.FraudCheckResult;
import com.Bank_website.fraud.dto.TransferCompletedEvent;
import com.Bank_website.fraud.entity.FraudAlert;
import com.Bank_website.fraud.repository.FraudAlertRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class FraudService {

    private static final Logger log = LoggerFactory.getLogger(FraudService.class);

    public static final String HIGH_VALUE = "HIGH_VALUE";
    public static final String VELOCITY = "VELOCITY";
    public static final String HIGH_RISK_CUSTOMER = "HIGH_RISK_CUSTOMER";
    public static final String HIGH_RISK_ONBOARDING = "HIGH_RISK_ONBOARDING";

    private final FraudRuleEngine ruleEngine;
    private final FraudAlertRepository alertRepository;
    private final RestClient customerClient;
    private final ObjectMapper objectMapper;

    public FraudService(FraudRuleEngine ruleEngine,
                        FraudAlertRepository alertRepository,
                        @Qualifier("customerServiceClient") RestClient customerClient,
                        ObjectMapper objectMapper) {
        this.ruleEngine = ruleEngine;
        this.alertRepository = alertRepository;
        this.customerClient = customerClient;
        this.objectMapper = objectMapper;
    }

    public FraudCheckResult checkTransfer(TransferCompletedEvent event) {
        // QStash delivers at-least-once: a replay returns the stored verdict, never double-counts.
        if (ruleEngine.isProcessed(event.transactionId())) {
            List<String> stored = alertRepository.findByTransactionId(event.transactionId()).stream()
                    .map(FraudAlert::getRuleFired).toList();
            return new FraudCheckResult(event.transactionId(), !stored.isEmpty(), stored);
        }

        List<String> rules = new ArrayList<>();
        if (ruleEngine.checkHighValue(event.amountCents())) {
            rules.add(HIGH_VALUE);
        }
        if (ruleEngine.checkVelocity(event.fromAccount())) {
            rules.add(VELOCITY);
        }
        if (ruleEngine.checkCustomerRisk(event.fromAccount())) {
            rules.add(HIGH_RISK_CUSTOMER);
        }

        for (String rule : rules) {
            if (!alertRepository.existsByTransactionIdAndRuleFired(event.transactionId(), rule)) {
                alertRepository.save(buildAlert(event, rule));
            }
            log.warn("FRAUD ALERT rule={} txn={} account={} amountCents={}",
                    rule, event.transactionId(), event.fromAccount(), event.amountCents());
        }

        ruleEngine.recordTransaction(event.fromAccount());
        ruleEngine.markProcessed(event.transactionId());
        return new FraudCheckResult(event.transactionId(), !rules.isEmpty(), rules);
    }

    /**
     * Initial risk scoring. If the event says the customer is a PEP or from a high-risk country,
     * the Customer Service is told to set aml_risk_rating = 'high' and an alert is stored.
     */
    public CustomerScoreResult scoreNewCustomer(CustomerCreatedEvent event) {
        boolean risky = Boolean.TRUE.equals(event.isPep()) || "high".equalsIgnoreCase(event.countryRisk());
        if (!risky) {
            log.info("Customer {} scored low risk", event.customerId());
            return new CustomerScoreResult(event.customerId(), "low", false);
        }

        customerClient.patch()
                .uri("/api/customers/{id}/aml-risk", event.customerId())
                .body(Map.of("amlRiskRating", "high"))
                .retrieve()
                .toBodilessEntity();

        FraudAlert alert = new FraudAlert();
        alert.setRuleFired(HIGH_RISK_ONBOARDING);
        alert.setSeverity("high");
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("customer_id", event.customerId());
        details.put("is_pep", event.isPep());
        details.put("country_risk", event.countryRisk());
        alert.setDetails(toJson(details));
        alertRepository.save(alert);

        log.warn("Customer {} escalated to HIGH aml risk at onboarding", event.customerId());
        return new CustomerScoreResult(event.customerId(), "high", true);
    }

    private FraudAlert buildAlert(TransferCompletedEvent event, String rule) {
        FraudAlert alert = new FraudAlert();
        alert.setTransactionId(event.transactionId());
        alert.setAccountId(event.fromAccount());
        alert.setRuleFired(rule);
        alert.setSeverity(HIGH_VALUE.equals(rule) ? "medium" : "high");
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("amount_cents", event.amountCents());
        details.put("to_account", event.toAccount());
        details.put("event_created_at", event.createdAt());
        alert.setDetails(toJson(details));
        return alert;
    }

    private String toJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
