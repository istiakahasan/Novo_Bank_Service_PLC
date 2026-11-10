package com.Bank_website.rails.service;

import com.Bank_website.rails.dto.AccountSummaryDto;
import com.Bank_website.rails.exception.ServiceUnavailableException;
import com.Bank_website.security.SystemAuthInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Component
public class CustomerClient {

    private static final Logger log = LoggerFactory.getLogger(CustomerClient.class);

    private final RestClient restClient;

    public CustomerClient(@Value("${customer.service.url}") String baseUrl,
                          @Value("${security.system.service-id:payment-rail-service}") String serviceId,
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

    public List<AccountSummaryDto> getAccounts(UUID customerId) {
        try {
            List<AccountSummaryDto> accounts = restClient.get()
                    .uri("/api/customers/{id}/accounts", customerId)
                    .retrieve()
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        throw new ServiceUnavailableException("Customer Service unavailable (status " + res.getStatusCode() + ")");
                    })
                    .body(new ParameterizedTypeReference<List<AccountSummaryDto>>() {
                    });
            return accounts != null ? accounts : List.of();
        } catch (ServiceUnavailableException e) {
            throw e;
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 503) {
                throw new ServiceUnavailableException("Customer Service returned 503");
            }
            log.warn("Accounts lookup failed with status {}: {}", e.getStatusCode(), e.getMessage());
            throw new ServiceUnavailableException("Customer Service accounts lookup failed: " + e.getMessage());
        } catch (RestClientException e) {
            log.warn("Customer Service call failed: {}", e.getMessage());
            throw new ServiceUnavailableException("Customer Service unreachable: " + e.getMessage());
        }
    }
}
