package com.Bank_website.auth;

import com.Bank_website.auth.dto.AuthDtos.CreateUserRequest;
import com.Bank_website.auth.dto.AuthDtos.TokenResponse;
import com.Bank_website.auth.entity.AuthUser;
import com.Bank_website.auth.entity.RefreshToken;
import com.Bank_website.auth.repository.AuthUserRepository;
import com.Bank_website.auth.repository.RefreshTokenRepository;
import com.Bank_website.auth.service.AuthException;
import com.Bank_website.auth.service.AuthService;
import com.Bank_website.auth.service.TokenService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.security.KeyPairGenerator;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private final AuthUserRepository users = mock(AuthUserRepository.class);
    private final RefreshTokenRepository refreshTokens = mock(RefreshTokenRepository.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> ops = mock(ValueOperations.class);
    private TokenService tokens;
    private AuthService service;
    private AuthUser alice;
    private RefreshToken lastSaved;

    @BeforeEach
    void setUp() throws Exception {
        var gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        String privateKey = Base64.getEncoder().encodeToString(gen.generateKeyPair().getPrivate().getEncoded());
        tokens = new TokenService(privateKey, "novo-auth", 900);
        when(redis.opsForValue()).thenReturn(ops);
        service = new AuthService(users, refreshTokens, tokens, redis, 604800, 5);

        alice = new AuthUser();
        alice.setId(UUID.randomUUID());
        alice.setUsername("alice");
        alice.setPasswordHash(new BCryptPasswordEncoder().encode("correct-horse-battery"));
        alice.setRole("CUSTOMER");
        alice.setCustomerId(UUID.randomUUID());
        alice.setEnabled(true);
        when(users.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(users.findById(alice.getId())).thenReturn(Optional.of(alice));
        when(refreshTokens.save(any(RefreshToken.class))).thenAnswer(i -> {
            lastSaved = i.getArgument(0);
            return lastSaved;
        });
    }

    @Test
    void loginIssuesVerifiableRs256TokenWithRoleAndCustomerClaims() {
        TokenResponse r = service.login("alice", "correct-horse-battery");
        Claims claims = tokens.parseAccessToken(r.accessToken());
        assertEquals(alice.getId().toString(), claims.getSubject());
        assertEquals(List.of("CUSTOMER"), claims.get("roles"));
        assertEquals(alice.getCustomerId().toString(), claims.get("cid"));
        assertEquals("Bearer", r.tokenType());
        assertEquals(900, r.expiresIn());
        assertNotEquals(r.refreshToken(), lastSaved.getTokenHash()); // only the hash is stored
    }

    @Test
    void wrongPasswordAndUnknownUserGetTheSame401AndCountFailures() {
        AuthException a = assertThrows(AuthException.class, () -> service.login("alice", "nope"));
        AuthException b = assertThrows(AuthException.class, () -> service.login("ghost", "nope"));
        assertEquals(HttpStatus.UNAUTHORIZED, a.status());
        assertEquals(a.getMessage(), b.getMessage());
        verify(ops, org.mockito.Mockito.times(2)).increment(anyString());
    }

    @Test
    void lockedOutAfterTooManyFailures() {
        when(ops.get("login_fail:alice")).thenReturn("5");
        AuthException e = assertThrows(AuthException.class, () -> service.login("alice", "correct-horse-battery"));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, e.status());
    }

    @Test
    void refreshRotatesAndRevokesTheOldToken() {
        TokenResponse first = service.login("alice", "correct-horse-battery");
        RefreshToken stored = lastSaved;
        when(refreshTokens.findByTokenHash(stored.getTokenHash())).thenReturn(Optional.of(stored));

        TokenResponse second = service.refresh(first.refreshToken());

        assertTrue(stored.isRevoked());
        assertNotEquals(first.refreshToken(), second.refreshToken());
    }

    @Test
    void reusedRefreshTokenRevokesEverySession() {
        TokenResponse first = service.login("alice", "correct-horse-battery");
        RefreshToken stored = lastSaved;
        stored.setRevoked(true);
        when(refreshTokens.findByTokenHash(stored.getTokenHash())).thenReturn(Optional.of(stored));

        AuthException e = assertThrows(AuthException.class, () -> service.refresh(first.refreshToken()));

        assertEquals(HttpStatus.UNAUTHORIZED, e.status());
        verify(refreshTokens).revokeAllForUser(alice.getId());
    }

    @Test
    void expiredRefreshTokenIsRejected() {
        TokenResponse first = service.login("alice", "correct-horse-battery");
        RefreshToken stored = lastSaved;
        stored.setExpiresAt(OffsetDateTime.now().minusMinutes(1));
        when(refreshTokens.findByTokenHash(stored.getTokenHash())).thenReturn(Optional.of(stored));
        assertThrows(AuthException.class, () -> service.refresh(first.refreshToken()));
    }

    @Test
    void logoutBlocklistsAccessTokenJtiWithRemainingTtl() {
        TokenResponse r = service.login("alice", "correct-horse-battery");
        Claims claims = tokens.parseAccessToken(r.accessToken());
        service.logout(claims, r.refreshToken());
        verify(ops).set(eq("revoked:jti:" + claims.getId()), eq("1"), any(Duration.class));
    }

    @Test
    void blocklistedAccessTokenIsNoLongerAuthenticated() {
        TokenResponse r = service.login("alice", "correct-horse-battery");
        Claims claims = tokens.parseAccessToken(r.accessToken());
        when(redis.hasKey("revoked:jti:" + claims.getId())).thenReturn(true);
        assertThrows(AuthException.class, () -> service.authenticate("Bearer " + r.accessToken()));
        assertThrows(AuthException.class, () -> service.authenticate(null));
        assertThrows(AuthException.class, () -> service.authenticate("Bearer garbage"));
    }

    @Test
    void userCreationEnforcesRoleCustomerRules() {
        assertThrows(AuthException.class,
                () -> service.createUser(new CreateUserRequest("c1", "longenoughpw", "CUSTOMER", null)));
        assertThrows(AuthException.class,
                () -> service.createUser(new CreateUserRequest("s1", "longenoughpw", "STAFF", UUID.randomUUID())));
        assertFalse(service.createUser(new CreateUserRequest("s2", "longenoughpw", "STAFF", null)).id() == null);
        verify(users, never()).save(org.mockito.ArgumentMatchers.argThat(u -> "c1".equals(u.getUsername())));
    }
}
