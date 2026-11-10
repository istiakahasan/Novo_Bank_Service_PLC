package com.Bank_website.cards.service;

import com.Bank_website.cards.dto.FraudCheckResultDto;
import com.Bank_website.security.SystemAuthInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Component
public class FraudClient {

    private static final Logger log = LoggerFactory.getLogger(FraudClient.class);

    private final RestClient restClient;

    public FraudClient(@Value("${fraud.service.url}") String baseUrl,
                       @Value("${security.system.service-id:card-service}") String serviceId,
                       @Value("${security.system.service-key:}") String serviceKey) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(5).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(5).toMillis());

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl.replaceAll("/+$", ""))
                .requestFactory(factory)
                .requestInterceptor(new SystemAuthInterceptor(serviceId, serviceKey))
                .build();
    }

    public boolean isFlagged(UUID accountId, Long amountCents, UUID settlementAccountId) {
        try {
            Map<String, Object> payload = Map.of(
                    "event", "transfer.completed",
                    "transaction_id", UUID.randomUUID().toString(),
                    "from_account", accountId.toString(),
                    "to_account", settlementAccountId.toString(),
                    "amount_cents", amountCents,
                    "created_at", Instant.now().toString()
            );

            FraudCheckResultDto result = restClient.post()
                    .uri("/webhooks/transfer-completed")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(FraudCheckResultDto.class);

            if (result != null && result.flagged()) {
                log.warn("Fraud check flagged for account {} amount {}: rules={}", accountId, amountCents, result.rules());
                return true;
            }
            return false;
        } catch (RestClientException ex) {
            log.warn("Fraud service check call failed or bypassed: {}", ex.getMessage());
            return false;
        }
    }
}
