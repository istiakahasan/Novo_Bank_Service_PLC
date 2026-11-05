package com.Bank_website.Novo_Bank_Service_PLC.service;

import com.Bank_website.Novo_Bank_Service_PLC.dto.AccountSummaryResponse;
import com.Bank_website.Novo_Bank_Service_PLC.exception.ServiceUnavailableException;
import com.Bank_website.Novo_Bank_Service_PLC.ledger.entity.Account;
import com.Bank_website.Novo_Bank_Service_PLC.ledger.repository.AccountRepository;
import com.Bank_website.security.SystemAuthInterceptor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Reads a customer's accounts from the Ledger Service: GET {ledger.service.url}/api/accounts?customerId={id},
 * authenticated with the SYSTEM service key (X-Service-Id / X-Service-Key from system.service-id and
 * system.token.secret), 5 s timeouts, answers cached in Redis for 60 s under customer_accounts:{customerId}.
 *
 * When ledger.service.url is empty (Customer and Ledger deployed as one app, the current layout) the same data
 * is read in-process from the account table instead of calling this service over HTTP.
 * Any Ledger failure (timeout, connection error, 4xx/5xx) becomes ServiceUnavailableException (HTTP 503).
 */
@Component
public class LedgerClient {

    private static final Logger log = LoggerFactory.getLogger(LedgerClient.class);
    static final String CACHE_PREFIX = "customer_accounts:";
    static final Duration CACHE_TTL = Duration.ofSeconds(60);

    private final RestClient restClient; // null = in-process mode
    private final AccountRepository accountRepository;
    private final RedisTemplate<String, String> redis;
    private final ObjectMapper mapper;

    @Autowired
    public LedgerClient(@Value("${ledger.service.url:}") String baseUrl,
                        @Value("${system.service-id:customer-service}") String serviceId,
                        @Value("${system.token.secret:}") String secret,
                        AccountRepository accountRepository,
                        RedisTemplate<String, String> redis,
                        ObjectMapper mapper) {
        this(baseUrl == null || baseUrl.isBlank() ? null : buildClient(baseUrl, serviceId, secret), accountRepository, redis, mapper);
    }

    LedgerClient(RestClient restClient, AccountRepository accountRepository, RedisTemplate<String, String> redis, ObjectMapper mapper) {
        this.restClient = restClient;
        this.accountRepository = accountRepository;
        this.redis = redis;
        this.mapper = mapper;
    }

    private static RestClient buildClient(String baseUrl, String serviceId, String secret) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(5).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(5).toMillis());
        return RestClient.builder()
                .baseUrl(baseUrl.replaceAll("/+$", ""))
                .requestFactory(factory)
                .requestInterceptor(new SystemAuthInterceptor(serviceId, secret))
                .build();
    }

    public List<AccountSummaryResponse> getAccountsByCustomer(UUID customerId) {
        String key = CACHE_PREFIX + customerId;
        List<AccountSummaryResponse> cached = readCache(key);
        if (cached != null) {
            return cached;
        }
        List<AccountSummaryResponse> accounts = restClient == null ? readLocally(customerId) : fetchOverHttp(customerId);
        writeCache(key, accounts);
        return accounts;
    }

    private List<AccountSummaryResponse> fetchOverHttp(UUID customerId) {
        try {
            List<AccountSummaryResponse> accounts = restClient.get()
                    .uri(uri -> uri.path("/api/accounts").queryParam("customerId", customerId).build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<AccountSummaryResponse>>() {
                    });
            return accounts == null ? List.of() : accounts;
        } catch (RestClientException e) {
            log.warn("Ledger Service call failed for customer {}: {}", customerId, e.getMessage());
            throw new ServiceUnavailableException("Ledger service unavailable, try again shortly");
        }
    }

    private List<AccountSummaryResponse> readLocally(UUID customerId) {
        return accountRepository.findByCustomerId(customerId).stream()
                .sorted(Comparator.comparing(Account::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(a -> new AccountSummaryResponse(a.getId(), a.getAccountNumber(), a.getAccountType(),
                        a.getBalanceCents(), a.getStatus(), a.getCreatedAt()))
                .toList();
    }

    private List<AccountSummaryResponse> readCache(String key) {
        try {
            String json = redis.opsForValue().get(key);
            return json == null ? null : mapper.readValue(json, new TypeReference<List<AccountSummaryResponse>>() {
            });
        } catch (JsonProcessingException | RuntimeException e) {
            log.warn("Ignoring unreadable cache entry {}: {}", key, e.getMessage()); // a cache problem must never fail the request
            return null;
        }
    }

    private void writeCache(String key, List<AccountSummaryResponse> accounts) {
        try {
            redis.opsForValue().set(key, mapper.writeValueAsString(accounts), CACHE_TTL);
        } catch (JsonProcessingException | RuntimeException e) {
            log.warn("Could not cache {}: {}", key, e.getMessage());
        }
    }
}
