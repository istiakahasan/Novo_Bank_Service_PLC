package com.Bank_website.Novo_Bank_Service_PLC.security;

import com.Bank_website.Novo_Bank_Service_PLC.controller.CustomerController;
import com.Bank_website.Novo_Bank_Service_PLC.dto.CustomerResponse;
import com.Bank_website.Novo_Bank_Service_PLC.ledger.controller.AccountController;
import com.Bank_website.Novo_Bank_Service_PLC.ledger.controller.TransferController;
import com.Bank_website.Novo_Bank_Service_PLC.ledger.dto.BalanceResponse;
import com.Bank_website.Novo_Bank_Service_PLC.ledger.repository.AccountRepository;
import com.Bank_website.Novo_Bank_Service_PLC.ledger.repository.TransactionRepository;
import com.Bank_website.Novo_Bank_Service_PLC.ledger.service.AccountService;
import com.Bank_website.Novo_Bank_Service_PLC.ledger.service.TransferService;
import com.Bank_website.Novo_Bank_Service_PLC.service.CustomerService;
import com.Bank_website.security.SecurityConfig;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {CustomerController.class, TransferController.class, AccountController.class,
        com.Bank_website.Novo_Bank_Service_PLC.ledger.controller.LedgerAccountController.class})
@Import({SecurityConfig.class, ServiceSecurityConfig.class, AccessPolicy.class,
        com.Bank_website.Novo_Bank_Service_PLC.ledger.mapper.TransferMapper.class})
class SecurityAuthorizationTest {

    private static final KeyPair KEYS = newKeys();
    private static final KeyPair ATTACKER_KEYS = newKeys();

    private static KeyPair newKeys() {
        try {
            KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
            g.initialize(2048);
            return g.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("security.jwt.public-key", () -> Base64.getEncoder().encodeToString(KEYS.getPublic().getEncoded()));
        registry.add("security.system.keys", () -> "ledger=ledger-key;fraud-service=fraud-key-1,fraud-key-2");
    }

    @Autowired MockMvc mvc;
    @MockBean CustomerService customerService;
    @MockBean TransferService transferService;
    @MockBean AccountService accountService;
    @MockBean AccountRepository accountRepository;
    @MockBean TransactionRepository transactionRepository;

    private final UUID aliceCustomer = UUID.randomUUID();
    private final UUID bobCustomer = UUID.randomUUID();
    private final UUID aliceAccount = UUID.randomUUID();
    private final UUID bobAccount = UUID.randomUUID();

    private String token(KeyPair signer, List<String> roles, UUID customerId, long ttlSeconds) {
        var b = Jwts.builder().issuer("novo-auth").subject("user-" + UUID.randomUUID()).id(UUID.randomUUID().toString())
                .claim("roles", roles).issuedAt(new Date())
                .expiration(Date.from(Instant.now().plusSeconds(ttlSeconds)));
        if (customerId != null) {
            b.claim("cid", customerId.toString());
        }
        return b.signWith(signer.getPrivate(), Jwts.SIG.RS256).compact();
    }

    private String alice() { return "Bearer " + token(KEYS, List.of("CUSTOMER"), aliceCustomer, 300); }
    private String staff() { return "Bearer " + token(KEYS, List.of("STAFF"), null, 300); }

    private void accountsExist() {
        when(accountRepository.findCustomerIdByAccountId(aliceAccount)).thenReturn(Optional.of(aliceCustomer));
        when(accountRepository.findCustomerIdByAccountId(bobAccount)).thenReturn(Optional.of(bobCustomer));
    }

    private static final String TRANSFER_BODY =
            "{\"fromAccount\":\"%s\",\"toAccount\":\"%s\",\"amountCents\":100,\"idempotencyKey\":\"k1\"}";

    // 1
    @Test
    void anonymousTransferIs401() throws Exception {
        mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON)
                .content(TRANSFER_BODY.formatted(aliceAccount, bobAccount))).andExpect(status().isUnauthorized());
    }

    // 2
    @Test
    void customerCannotCreateCustomer() throws Exception {
        mvc.perform(post("/api/customers").header("Authorization", alice()).contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isForbidden());
    }

    // 3 + own account allowed
    @Test
    void customerCanReadOwnBalanceButNotSomeoneElses() throws Exception {
        accountsExist();
        when(transferService.getBalance(aliceAccount)).thenReturn(new BalanceResponse(aliceAccount, 100L, "USD"));
        mvc.perform(get("/api/accounts/" + aliceAccount + "/balance").header("Authorization", alice()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/accounts/" + bobAccount + "/balance").header("Authorization", alice()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/accounts/" + bobAccount + "/entries").header("Authorization", alice()))
                .andExpect(status().isForbidden());
    }

    // 4 + 5
    @Test
    void onlyStaffCanUpdateKyc() throws Exception {
        UUID id = UUID.randomUUID();
        String body = "{\"kycStatus\":\"verified\",\"verifiedBy\":\"officer-1\"}";
        when(customerService.updateKyc(any(), any())).thenReturn(
                new CustomerResponse(id, "CIF-000001", "A B", "01712345678", null, "verified", "low", "active", null));
        mvc.perform(patch("/api/customers/" + id + "/kyc").header("Authorization", alice())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(patch("/api/customers/" + id + "/kyc").header("Authorization", staff())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
        mvc.perform(patch("/api/customers/" + id + "/kyc").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }

    // 6
    @Test
    void expiredTokenIs401() throws Exception {
        String expired = "Bearer " + token(KEYS, List.of("STAFF"), null, -3600);
        mvc.perform(get("/api/accounts/" + aliceAccount + "/balance").header("Authorization", expired))
                .andExpect(status().isUnauthorized());
    }

    // 7
    @Test
    void tamperedRoleClaimIs401() throws Exception {
        String[] parts = token(KEYS, List.of("CUSTOMER"), aliceCustomer, 300).split("\\.");
        String forgedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("{\"iss\":\"novo-auth\",\"sub\":\"x\",\"roles\":[\"STAFF\"],\"exp\":" + (Instant.now().getEpochSecond() + 300) + "}")
                        .getBytes(StandardCharsets.UTF_8));
        mvc.perform(post("/api/customers").header("Authorization", "Bearer " + parts[0] + "." + forgedPayload + "." + parts[2])
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
    }

    @Test
    void tokenSignedByAnotherKeyIs401() throws Exception {
        String forged = "Bearer " + token(ATTACKER_KEYS, List.of("STAFF"), null, 300);
        mvc.perform(get("/api/customers/" + UUID.randomUUID()).header("Authorization", forged))
                .andExpect(status().isUnauthorized());
    }

    // 8
    @Test
    void systemKeyCanCallByAccount() throws Exception {
        UUID customerId = UUID.randomUUID();
        when(customerService.getByAccountId(aliceAccount)).thenReturn(
                new CustomerResponse(customerId, "CIF-000001", "A B", "01712345678", null, "verified", "low", "active", null));
        mvc.perform(get("/api/customers/by-account/" + aliceAccount)
                        .header("X-Service-Id", "ledger").header("X-Service-Key", "ledger-key"))
                .andExpect(status().isOk());
        // rotation: either of the fraud-service keys works
        mvc.perform(get("/api/customers/by-account/" + aliceAccount)
                        .header("X-Service-Id", "fraud-service").header("X-Service-Key", "fraud-key-2"))
                .andExpect(status().isOk());
    }

    // 9
    @Test
    void customerTokenCannotReadSomeoneElsesCustomerViaByAccount() throws Exception {
        accountsExist();
        mvc.perform(get("/api/customers/by-account/" + bobAccount).header("Authorization", alice()))
                .andExpect(status().isForbidden());
    }

    @Test
    void wrongServiceKeyIs401AndSystemCannotTouchTheLedger() throws Exception {
        mvc.perform(get("/api/customers/by-account/" + aliceAccount)
                        .header("X-Service-Id", "ledger").header("X-Service-Key", "wrong"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/customers/by-account/" + aliceAccount)
                        .header("X-Service-Id", "unknown").header("X-Service-Key", "ledger-key"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/transfers").header("X-Service-Id", "ledger").header("X-Service-Key", "ledger-key")
                .contentType(MediaType.APPLICATION_JSON).content(TRANSFER_BODY.formatted(aliceAccount, bobAccount)))
                .andExpect(status().isForbidden());
    }

    @Test
    void systemRoleClaimInUserTokenGivesNoPower() throws Exception {
        String forged = "Bearer " + token(KEYS, List.of("SYSTEM"), null, 300);
        mvc.perform(get("/api/customers/by-mobile/01712345678").header("Authorization", forged))
                .andExpect(status().isForbidden());
    }

    @Test
    void customerCannotTransferFromSomeoneElsesAccountOrOpenAccountForAnotherCustomer() throws Exception {
        accountsExist();
        mvc.perform(post("/api/transfers").header("Authorization", alice()).contentType(MediaType.APPLICATION_JSON)
                .content(TRANSFER_BODY.formatted(bobAccount, aliceAccount))).andExpect(status().isForbidden());
        mvc.perform(post("/api/accounts").header("Authorization", alice()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"customerId\":\"" + bobCustomer + "\",\"accountType\":\"savings\",\"initialDepositCents\":100}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void customerMayTransferFromOwnAccount() throws Exception {
        accountsExist();
        when(transferService.transfer(any())).thenReturn(new TransferService.TransferOutcome(
                new com.Bank_website.Novo_Bank_Service_PLC.ledger.dto.TransferResponse(UUID.randomUUID(), "completed", 100L, null), false));
        mvc.perform(post("/api/transfers").header("Authorization", alice()).contentType(MediaType.APPLICATION_JSON)
                .content(TRANSFER_BODY.formatted(aliceAccount, bobAccount))).andExpect(status().isCreated());
    }

    // ---- GET /api/customers, /me, /{id}/accounts and the SYSTEM-only ledger lookup

    @Test
    void customerListIsStaffOnly() throws Exception {
        when(customerService.listCustomers(any(), any(), any(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(new com.Bank_website.Novo_Bank_Service_PLC.dto.PaginatedCustomerResponse(List.of(), 0, 20, 0, 0));
        mvc.perform(get("/api/customers?kycStatus=PENDING&page=0&size=20").header("Authorization", staff()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/customers").header("Authorization", alice())).andExpect(status().isForbidden());
        mvc.perform(get("/api/customers")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/customers").header("X-Service-Id", "ledger").header("X-Service-Key", "ledger-key"))
                .andExpect(status().isForbidden());
    }

    @Test
    void badListFiltersAre400() throws Exception {
        when(customerService.listCustomers(any(), any(), any(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt()))
                .thenThrow(new com.Bank_website.Novo_Bank_Service_PLC.exception.BadRequestException("kycStatus must be one of ..."));
        mvc.perform(get("/api/customers?kycStatus=MAYBE").header("Authorization", staff())).andExpect(status().isBadRequest());
    }

    @Test
    void meIsCustomerOnlyAndUsesTheTokenCustomerId() throws Exception {
        when(customerService.getById(aliceCustomer)).thenReturn(
                new CustomerResponse(aliceCustomer, "CIF-000001", "A B", "01712345678", null, "verified", "low", "active", null));
        mvc.perform(get("/api/customers/me").header("Authorization", alice())).andExpect(status().isOk());
        org.mockito.Mockito.verify(customerService).getById(aliceCustomer);
        mvc.perform(get("/api/customers/me").header("Authorization", staff())).andExpect(status().isForbidden());
        mvc.perform(get("/api/customers/me")).andExpect(status().isUnauthorized());
        // a CUSTOMER token without a customerId claim -> 403
        String noCid = "Bearer " + token(KEYS, List.of("CUSTOMER"), null, 300);
        mvc.perform(get("/api/customers/me").header("Authorization", noCid)).andExpect(status().isForbidden());
    }

    @Test
    void customerAccountsOwnOtherStaffAndLedgerOutage() throws Exception {
        when(customerService.getAccounts(aliceCustomer)).thenReturn(List.of());
        mvc.perform(get("/api/customers/" + aliceCustomer + "/accounts").header("Authorization", alice())).andExpect(status().isOk());
        mvc.perform(get("/api/customers/" + bobCustomer + "/accounts").header("Authorization", alice())).andExpect(status().isForbidden());
        mvc.perform(get("/api/customers/" + bobCustomer + "/accounts").header("Authorization", staff())).andExpect(status().isOk());
        mvc.perform(get("/api/customers/" + aliceCustomer + "/accounts")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/customers/" + aliceCustomer + "/accounts").header("X-Service-Id", "ledger").header("X-Service-Key", "ledger-key"))
                .andExpect(status().isForbidden());

        org.mockito.Mockito.doThrow(new com.Bank_website.Novo_Bank_Service_PLC.exception.ServiceUnavailableException("Ledger service unavailable, try again shortly"))
                .when(customerService).getAccounts(aliceCustomer);
        mvc.perform(get("/api/customers/" + aliceCustomer + "/accounts").header("Authorization", alice()))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void ledgerAccountsByCustomerIsSystemOnly() throws Exception {
        com.Bank_website.Novo_Bank_Service_PLC.ledger.entity.Account account = new com.Bank_website.Novo_Bank_Service_PLC.ledger.entity.Account();
        account.setId(aliceAccount);
        account.setAccountNumber("ACC-0000000001");
        account.setBalanceCents(100L);
        when(accountRepository.findByCustomerId(aliceCustomer)).thenReturn(List.of(account));

        mvc.perform(get("/api/accounts?customerId=" + aliceCustomer)
                        .header("X-Service-Id", "ledger").header("X-Service-Key", "ledger-key"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$[0].accountNumber").value("ACC-0000000001"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$[0].balanceCents").value(100));
        mvc.perform(get("/api/accounts?customerId=" + aliceCustomer).header("Authorization", staff())).andExpect(status().isForbidden());
        mvc.perform(get("/api/accounts?customerId=" + aliceCustomer).header("Authorization", alice())).andExpect(status().isForbidden());
        mvc.perform(get("/api/accounts?customerId=" + aliceCustomer)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/accounts?customerId=" + aliceCustomer)
                        .header("X-Service-Id", "ledger").header("X-Service-Key", "wrong"))
                .andExpect(status().isUnauthorized());
    }
}
