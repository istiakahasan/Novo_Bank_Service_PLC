package com.Bank_website.rails.service;

import com.Bank_website.rails.dto.AccountSummaryDto;
import com.Bank_website.rails.dto.TransferRequestDto;
import com.Bank_website.rails.dto.TransferResponseDto;
import com.Bank_website.rails.exception.ResourceNotFoundException;
import com.Bank_website.rails.exception.ServiceUnavailableException;
import com.Bank_website.security.SystemAuthInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.Optional;

@Component
public class LedgerClient {

    private static final Logger log = LoggerFactory.getLogger(LedgerClient.class);

    private final RestClient restClient;

    public LedgerClient(@Value("${ledger.service.url}") String baseUrl,
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

    public TransferResponseDto postTransfer(TransferRequestDto request) {
        try {
            return restClient.post()
                    .uri("/api/transfers")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .onStatus(status -> status.value() == 422, (req, res) -> {
                        throw new IllegalArgumentException("Ledger transfer rejected: insufficient funds or unprocessable transfer");
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        throw new ServiceUnavailableException("Ledger Service unavailable (status " + res.getStatusCode() + ")");
                    })
                    .body(TransferResponseDto.class);
        } catch (IllegalArgumentException | ServiceUnavailableException e) {
            throw e;
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 422) {
                throw new IllegalArgumentException("Ledger transfer rejected: insufficient funds or unprocessable transfer");
            }
            if (e.getStatusCode().value() == 503) {
                throw new ServiceUnavailableException("Ledger Service returned 503");
            }
            log.warn("Ledger transfer failed with status {}: {}", e.getStatusCode(), e.getMessage());
            throw new ServiceUnavailableException("Ledger transfer failed: " + e.getMessage());
        } catch (RestClientException e) {
            log.warn("Ledger Service call failed: {}", e.getMessage());
            throw new ServiceUnavailableException("Ledger Service unreachable: " + e.getMessage());
        }
    }

    public Optional<AccountSummaryDto> getAccountByAccountNumber(String accountNumber) {
        try {
            AccountSummaryDto account = restClient.get()
                    .uri(uri -> uri.path("/api/accounts").queryParam("accountNumber", accountNumber).build())
                    .retrieve()
                    .onStatus(status -> status.value() == 404, (req, res) -> {
                        // handled by empty Optional
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        throw new ServiceUnavailableException("Ledger Service unavailable (status " + res.getStatusCode() + ")");
                    })
                    .body(AccountSummaryDto.class);
            return Optional.ofNullable(account);
        } catch (ServiceUnavailableException e) {
            throw e;
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                return Optional.empty();
            }
            if (e.getStatusCode().value() == 503) {
                throw new ServiceUnavailableException("Ledger Service returned 503");
            }
            log.warn("Ledger account lookup failed with status {}: {}", e.getStatusCode(), e.getMessage());
            throw new ServiceUnavailableException("Ledger account lookup failed: " + e.getMessage());
        } catch (RestClientException e) {
            log.warn("Ledger Service call failed: {}", e.getMessage());
            throw new ServiceUnavailableException("Ledger Service unreachable: " + e.getMessage());
        }
    }
}
