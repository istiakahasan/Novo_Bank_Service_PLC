package com.Bank_website.authserver;

import com.Bank_website.authserver.config.SecurityConfig;
import com.Bank_website.authserver.controller.AdminController;
import com.Bank_website.authserver.controller.AuthController;
import com.Bank_website.authserver.controller.JwksController;
import com.Bank_website.authserver.dto.LoginResponse;
import com.Bank_website.authserver.entity.AuthUser;
import com.Bank_website.authserver.exception.AccountLockedException;
import com.Bank_website.authserver.exception.GlobalExceptionHandler;
import com.Bank_website.authserver.exception.InvalidCredentialsException;
import com.Bank_website.authserver.exception.RateLimitExceededException;
import com.Bank_website.authserver.service.AuthService;
import com.Bank_website.authserver.service.JwtService;
import com.Bank_website.authserver.service.RedisBlocklistService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {AuthController.class, AdminController.class, JwksController.class})
@Import({SecurityConfig.class, JwtService.class, GlobalExceptionHandler.class})
class AuthWebTest {

    private static final KeyPair KEYS = newKeys();

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
    static void props(DynamicPropertyRegistry registry) {
        registry.add("jwt.private-key", () -> Base64.getEncoder().encodeToString(KEYS.getPrivate().getEncoded()));
        registry.add("jwt.public-key", () -> "");
        registry.add("jwt.issuer", () -> "banking-auth");
        registry.add("jwt.access-ttl-seconds", () -> "900");
    }

    @Autowired MockMvc mvc;
    @Autowired JwtService jwtService;
    @MockBean AuthService authService;
    @MockBean RedisBlocklistService blocklist;

    private String bearer(String role) {
        AuthUser u = new AuthUser();
        u.setUserId(UUID.randomUUID());
        u.setRole(role);
        return "Bearer " + jwtService.generateAccessToken(u).token();
    }

    private static final String LOGIN = "{\"email\":\"alice@example.com\",\"password\":\"Correct-Horse-9-Battery\"}";

    @Test
    void unlockIsStaffOnly() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(post("/api/auth/admin/unlock/" + id)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/admin/unlock/" + id).header("Authorization", bearer("CUSTOMER"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/admin/unlock/" + id).header("Authorization", bearer("SYSTEM"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/admin/unlock/" + id).header("Authorization", bearer("STAFF")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.unlocked").value(true));
        verify(authService).unlock(id);
    }

    @Test
    void aBadOrBlocklistedBearerIs401EvenOnPublicEndpoints() throws Exception {
        mvc.perform(post("/api/auth/admin/unlock/" + UUID.randomUUID()).header("Authorization", "Bearer garbage"))
                .andExpect(status().isUnauthorized());
        String token = bearer("STAFF");
        when(blocklist.isBlocklisted(anyString())).thenReturn(true);
        mvc.perform(post("/api/auth/admin/unlock/" + UUID.randomUUID()).header("Authorization", token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void jwksIsPublicAndWellFormed() throws Exception {
        for (String path : new String[]{"/api/auth/.well-known/jwks.json", "/.well-known/jwks.json"}) {
            mvc.perform(get(path))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
                    .andExpect(jsonPath("$.keys[0].kid").value("v1"))
                    .andExpect(jsonPath("$.keys[0].use").value("sig"))
                    .andExpect(jsonPath("$.keys[0].alg").value("RS256"))
                    .andExpect(jsonPath("$.keys[0].e").value("AQAB"))
                    .andExpect(jsonPath("$.keys[0].n").isNotEmpty());
        }
    }

    @Test
    void successfulLoginReturnsTheSpecifiedShape() throws Exception {
        when(authService.login(any(), anyString())).thenReturn(new LoginResponse("access", "refresh", "CUSTOMER", UUID.randomUUID(), null, 900));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(LOGIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access"))
                .andExpect(jsonPath("$.refreshToken").value("refresh"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.expiresIn").value(900));
    }

    @Test
    void loginAcceptsUsernameAsAnAliasForEmail() throws Exception {
        when(authService.login(any(), anyString())).thenReturn(new LoginResponse("a", "r", "STAFF", null, UUID.randomUUID(), 900));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"staff@example.com\",\"password\":\"Correct-Horse-9-Battery\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void failuresMapToTheRightStatusWithAJsonBodyAndRetryAfter() throws Exception {
        when(authService.login(any(), anyString())).thenThrow(new InvalidCredentialsException());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(LOGIN))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid credentials"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Correct-Horse"))));

        doThrow(new AccountLockedException(900)).when(authService).login(any(), anyString());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(LOGIN))
                .andExpect(status().isLocked())
                .andExpect(header().string("Retry-After", "900"))
                .andExpect(jsonPath("$.status").value(423));

        doThrow(new RateLimitExceededException(30)).when(authService).login(any(), anyString());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(LOGIN))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "30"));
    }

    @Test
    void weakPasswordsAreRejectedBeforeAnythingElseAndTheErrorDoesNotEchoThem() throws Exception {
        String body = "{\"email\":\"a@b.co\",\"password\":\"alllowercase1!\",\"role\":\"CUSTOMER\"}";
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("alllowercase1!"))));
        String shortPw = "{\"email\":\"a@b.co\",\"password\":\"Short-1a\",\"role\":\"CUSTOMER\"}";
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(shortPw)).andExpect(status().isBadRequest());
        String badEmail = "{\"email\":\"nope\",\"password\":\"Correct-Horse-9-Battery\",\"role\":\"CUSTOMER\"}";
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(badEmail)).andExpect(status().isBadRequest());
        String badRole = "{\"email\":\"a@b.co\",\"password\":\"Correct-Horse-9-Battery\",\"role\":\"ADMIN\"}";
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(badRole)).andExpect(status().isBadRequest());
        verifyNoInteractions(authService);
    }

    @Test
    void otherPathsRequireAJwt() throws Exception {
        mvc.perform(get("/api/anything")).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutPassesTheBearerAndBodyToTheService() throws Exception {
        mvc.perform(post("/api/auth/logout").header("Authorization", bearer("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"refreshToken\":\"abc\"}"))
                .andExpect(status().isNoContent());
        verify(authService).logout(any(), any());
        doThrow(new com.Bank_website.authserver.exception.UnauthorizedException("Bearer token required"))
                .when(authService).logout(org.mockito.ArgumentMatchers.isNull(), any());
        mvc.perform(post("/api/auth/logout").contentType(MediaType.APPLICATION_JSON).content("{\"refreshToken\":\"abc\"}"))
                .andExpect(status().isUnauthorized());
    }
}
