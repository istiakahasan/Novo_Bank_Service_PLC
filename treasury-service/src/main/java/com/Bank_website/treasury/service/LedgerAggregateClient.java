package com.Bank_website.treasury.service;

import com.Bank_website.security.SystemAuthInterceptor;
import com.Bank_website.treasury.dto.AccountSummaryDto;
import com.Bank_website.treasury.exception.ServiceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;

@Component
public class LedgerAggregateClient {

    private static final Logger log = LoggerFactory.getLogger(LedgerAggregateClient.class);

    private final RestClient restClient;

    public LedgerAggregateClient(@Value("${ledger.service.url}") String baseUrl,
                                 @Value("${security.system.service-id:treasury-service}") String serviceId,
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

    public AccountSummaryDto getAccountSummary() {
        try {
            AccountSummaryDto summary = restClient.get()
                    .uri("/api/accounts/summary")
                    .retrieve()
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        throw new ServiceUnavailableException("Ledger Service unavailable (status " + res.getStatusCode() + ")");
                    })
                    .body(AccountSummaryDto.class);

            if (summary == null) {
                throw new ServiceUnavailableException("Ledger Service returned null summary");
            }
            return summary;
        } catch (ServiceUnavailableException e) {
            throw e;
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 503) {
                throw new ServiceUnavailableException("Ledger Service returned 503");
            }
            log.warn("Ledger summary lookup failed with status {}: {}", e.getStatusCode(), e.getMessage());
            throw new ServiceUnavailableException("Ledger Service summary failed: " + e.getMessage());
        } catch (RestClientException e) {
            log.warn("Ledger Service call failed: {}", e.getMessage());
            throw new ServiceUnavailableException("Ledger Service unreachable: " + e.getMessage());
        }
    }
}
