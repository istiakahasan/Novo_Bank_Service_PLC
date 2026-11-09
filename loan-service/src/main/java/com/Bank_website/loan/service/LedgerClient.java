package com.Bank_website.loan.service;

import com.Bank_website.loan.dto.AccountSummaryDto;
import com.Bank_website.loan.dto.TransferRequestDto;
import com.Bank_website.loan.dto.TransferResponseDto;
import com.Bank_website.loan.exception.InsufficientFundsException;
import com.Bank_website.loan.exception.ServiceUnavailableException;
import com.Bank_website.security.SystemAuthInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Component
public class LedgerClient {

    private static final Logger log = LoggerFactory.getLogger(LedgerClient.class);

    private final RestClient restClient;

    public LedgerClient(@Value("${ledger.service.url}") String baseUrl,
                        @Value("${security.system.service-id:loan-service}") String serviceId,
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
                        throw new InsufficientFundsException("Insufficient funds for transfer in Ledger");
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        throw new ServiceUnavailableException("Ledger Service unavailable (status " + res.getStatusCode() + ")");
                    })
                    .body(TransferResponseDto.class);
        } catch (InsufficientFundsException | ServiceUnavailableException e) {
            throw e;
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 422) {
                throw new InsufficientFundsException("Insufficient funds for transfer in Ledger");
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

    public List<AccountSummaryDto> getAccountsByCustomer(UUID customerId) {
        try {
            List<AccountSummaryDto> accounts = restClient.get()
                    .uri(uri -> uri.path("/api/accounts").queryParam("customerId", customerId).build())
                    .retrieve()
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        throw new ServiceUnavailableException("Ledger Service unavailable (status " + res.getStatusCode() + ")");
                    })
                    .body(new ParameterizedTypeReference<List<AccountSummaryDto>>() {
                    });
            return accounts != null ? accounts : List.of();
        } catch (ServiceUnavailableException e) {
            throw e;
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 503) {
                throw new ServiceUnavailableException("Ledger Service returned 503");
            }
            log.warn("Ledger accounts lookup failed with status {}: {}", e.getStatusCode(), e.getMessage());
            throw new ServiceUnavailableException("Ledger accounts lookup failed: " + e.getMessage());
        } catch (RestClientException e) {
            log.warn("Ledger Service call failed: {}", e.getMessage());
            throw new ServiceUnavailableException("Ledger Service unreachable: " + e.getMessage());
        }
    }
}
