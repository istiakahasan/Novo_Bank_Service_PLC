package com.Bank_website.Novo_Bank_Service_PLC;

import com.Bank_website.Novo_Bank_Service_PLC.dto.AccountSummaryResponse;
import com.Bank_website.Novo_Bank_Service_PLC.dto.PaginatedCustomerResponse;
import com.Bank_website.Novo_Bank_Service_PLC.entity.Customer;
import com.Bank_website.Novo_Bank_Service_PLC.event.EventPublisher;
import com.Bank_website.Novo_Bank_Service_PLC.exception.BadRequestException;
import com.Bank_website.Novo_Bank_Service_PLC.exception.ResourceNotFoundException;
import com.Bank_website.Novo_Bank_Service_PLC.exception.ServiceUnavailableException;
import com.Bank_website.Novo_Bank_Service_PLC.ledger.entity.Account;
import com.Bank_website.Novo_Bank_Service_PLC.ledger.repository.AccountRepository;
import com.Bank_website.Novo_Bank_Service_PLC.mapper.CustomerMapper;
import com.Bank_website.Novo_Bank_Service_PLC.repository.CustomerRepository;
import com.Bank_website.Novo_Bank_Service_PLC.service.CustomerService;
import com.Bank_website.Novo_Bank_Service_PLC.service.LedgerClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.lang.reflect.Constructor;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@SuppressWarnings({"unchecked", "rawtypes"})
class CustomerAccountsAndListingTest {

    private final CustomerRepository repo = mock(CustomerRepository.class);
    private final LedgerClient ledgerClient = mock(LedgerClient.class);
    private final CustomerService service = new CustomerService(repo, new CustomerMapper(), mock(EventPublisher.class),
            mock(AccountRepository.class), ledgerClient);
    private final UUID customerId = UUID.randomUUID();

    // ------------------------------------------------------------ GET /api/customers

    @Test
    void listFiltersAreCaseInsensitivePagedAndSizeIsCappedAt100() {
        Customer c = new Customer();
        c.setId(UUID.randomUUID());
        c.setKycStatus("pending");
        when(repo.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(java.util.Collections.nCopies(51, c), PageRequest.of(2, 100), 251));

        PaginatedCustomerResponse r = service.listCustomers("PENDING", "Active", "HIGH", 2, 500);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repo).findAll(any(Specification.class), pageable.capture());
        assertEquals(2, pageable.getValue().getPageNumber());
        assertEquals(100, pageable.getValue().getPageSize());
        assertEquals("createdAt: DESC", pageable.getValue().getSort().toString());
        assertEquals(51, r.content().size());
        assertEquals(251, r.totalElements());
        assertEquals(3, r.totalPages());
    }

    @Test
    void defaultsApplyToNonPositiveSizeAndNegativePage() {
        when(repo.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        service.listCustomers(null, null, null, -5, 0);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repo).findAll(any(Specification.class), pageable.capture());
        assertEquals(0, pageable.getValue().getPageNumber());
        assertEquals(20, pageable.getValue().getPageSize());
    }

    @Test
    void unknownFilterValuesAreRejected() {
        assertThrows(BadRequestException.class, () -> service.listCustomers("MAYBE", null, null, 0, 20));
        assertThrows(BadRequestException.class, () -> service.listCustomers(null, "deleted", null, 0, 20));
        assertThrows(BadRequestException.class, () -> service.listCustomers(null, null, "EXTREME", 0, 20));
        // all four documented kycStatus values are accepted
        when(repo.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        for (String status : List.of("PENDING", "VERIFIED", "REJECTED", "FLAGGED")) {
            service.listCustomers(status, null, null, 0, 20);
        }
    }

    // ------------------------------------------------------------ GET /api/customers/{id}/accounts

    @Test
    void accountsOfAnUnknownCustomerAre404AndTheLedgerIsNotCalled() {
        when(repo.existsById(customerId)).thenReturn(false);
        assertThrows(ResourceNotFoundException.class, () -> service.getAccounts(customerId));
        verify(ledgerClient, never()).getAccountsByCustomer(any());
    }

    @Test
    void accountsAreFetchedFromTheLedgerClientAndLedgerOutagesPropagateAs503() {
        when(repo.existsById(customerId)).thenReturn(true);
        List<AccountSummaryResponse> accounts = List.of(new AccountSummaryResponse(UUID.randomUUID(), "ACC-0000000001", "savings", 5L, "active", OffsetDateTime.now()));
        when(ledgerClient.getAccountsByCustomer(customerId)).thenReturn(accounts);
        assertEquals(accounts, service.getAccounts(customerId));

        when(ledgerClient.getAccountsByCustomer(customerId)).thenThrow(new ServiceUnavailableException("down"));
        assertThrows(ServiceUnavailableException.class, () -> service.getAccounts(customerId));
    }

    // ------------------------------------------------------------ LedgerClient

    private final RedisTemplate<String, String> redis = mock(RedisTemplate.class);
    private final ValueOperations<String, String> ops = mock(ValueOperations.class);
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private LedgerClient httpClient(MockRestServiceServer[] serverOut) throws Exception {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ledger.test");
        serverOut[0] = MockRestServiceServer.bindTo(builder).build();
        when(redis.opsForValue()).thenReturn(ops);
        Constructor<LedgerClient> ctor = LedgerClient.class.getDeclaredConstructor(RestClient.class, AccountRepository.class, RedisTemplate.class, ObjectMapper.class);
        ctor.setAccessible(true);
        return ctor.newInstance(builder.build(), mock(AccountRepository.class), redis, mapper);
    }

    private static final String LEDGER_JSON = "[{\"accountId\":\"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa\",\"accountNumber\":\"ACC-0000000001\","
            + "\"accountType\":\"savings\",\"balanceCents\":50000,\"status\":\"active\",\"createdAt\":\"2026-01-01T10:00:00Z\"}]";

    @Test
    void ledgerClientCallsTheSystemEndpointAndCachesTheAnswerForSixtySeconds() throws Exception {
        MockRestServiceServer[] server = new MockRestServiceServer[1];
        LedgerClient client = httpClient(server);
        server[0].expect(requestTo("http://ledger.test/api/accounts?customerId=" + customerId))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(LEDGER_JSON, MediaType.APPLICATION_JSON));

        List<AccountSummaryResponse> accounts = client.getAccountsByCustomer(customerId);

        assertEquals(1, accounts.size());
        assertEquals("ACC-0000000001", accounts.get(0).accountNumber());
        assertEquals(50000L, accounts.get(0).balanceCents());
        verify(ops).set(eq("customer_accounts:" + customerId), anyString(), eq(Duration.ofSeconds(60)));
        server[0].verify();
    }

    @Test
    void aCacheHitSkipsTheLedgerEntirely() throws Exception {
        MockRestServiceServer[] server = new MockRestServiceServer[1];
        LedgerClient client = httpClient(server);
        when(ops.get("customer_accounts:" + customerId)).thenReturn(LEDGER_JSON);

        assertEquals("ACC-0000000001", client.getAccountsByCustomer(customerId).get(0).accountNumber());
        server[0].verify(); // no HTTP call was expected or made
        verify(ops, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void ledgerServerErrorsTimeoutsAndRejectionsBecome503AndAreNotCached() throws Exception {
        MockRestServiceServer[] server = new MockRestServiceServer[1];
        LedgerClient client = httpClient(server);
        server[0].expect(requestTo("http://ledger.test/api/accounts?customerId=" + customerId))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        assertThrows(ServiceUnavailableException.class, () -> client.getAccountsByCustomer(customerId));

        server[0].reset();
        server[0].expect(requestTo("http://ledger.test/api/accounts?customerId=" + customerId))
                .andRespond(request -> {
                    throw new java.net.SocketTimeoutException("Read timed out");
                });
        assertThrows(ServiceUnavailableException.class, () -> client.getAccountsByCustomer(customerId));

        server[0].reset();
        server[0].expect(requestTo("http://ledger.test/api/accounts?customerId=" + customerId))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)); // wrong SYSTEM key on the ledger side
        assertThrows(ServiceUnavailableException.class, () -> client.getAccountsByCustomer(customerId));
        verify(ops, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void aBrokenCacheNeverFailsTheRequest() throws Exception {
        MockRestServiceServer[] server = new MockRestServiceServer[1];
        LedgerClient client = httpClient(server);
        when(ops.get(anyString())).thenThrow(new org.springframework.data.redis.RedisConnectionFailureException("down"));
        server[0].expect(requestTo("http://ledger.test/api/accounts?customerId=" + customerId))
                .andRespond(withSuccess(LEDGER_JSON, MediaType.APPLICATION_JSON));
        assertEquals(1, client.getAccountsByCustomer(customerId).size());
    }

    @Test
    void withoutALedgerUrlTheAccountsAreReadInProcess() {
        AccountRepository accounts = mock(AccountRepository.class);
        when(redis.opsForValue()).thenReturn(ops);
        Account a = new Account();
        a.setId(UUID.randomUUID());
        a.setAccountNumber("ACC-0000000002");
        a.setAccountType("checking");
        a.setBalanceCents(700L);
        a.setStatus("active");
        a.setCreatedAt(OffsetDateTime.now());
        when(accounts.findByCustomerId(customerId)).thenReturn(List.of(a));
        LedgerClient local = new LedgerClient("", "customer-service", "", accounts, redis, mapper);

        List<AccountSummaryResponse> result = local.getAccountsByCustomer(customerId);

        assertEquals("ACC-0000000002", result.get(0).accountNumber());
        verify(accounts, times(1)).findByCustomerId(customerId);
    }

    @Test
    void theSystemCredentialsAreSentToTheLedger() throws Exception {
        // the real constructor wires SystemAuthInterceptor; this asserts the headers on the wire
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ledger.test")
                .requestInterceptor(new com.Bank_website.security.SystemAuthInterceptor("customer-service", "s3cret"));
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://ledger.test/x"))
                .andExpect(header("X-Service-Id", "customer-service"))
                .andExpect(header("X-Service-Key", "s3cret"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        builder.build().get().uri("/x").retrieve().toBodilessEntity();
        server.verify();
    }
}
