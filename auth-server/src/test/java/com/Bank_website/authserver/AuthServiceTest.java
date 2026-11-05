package com.Bank_website.authserver;

import com.Bank_website.authserver.config.SecurityConfig;
import com.Bank_website.authserver.dto.LoginRequest;
import com.Bank_website.authserver.dto.LoginResponse;
import com.Bank_website.authserver.dto.RefreshRequest;
import com.Bank_website.authserver.dto.RegisterRequest;
import com.Bank_website.authserver.entity.AuthUser;
import com.Bank_website.authserver.entity.RefreshToken;
import com.Bank_website.authserver.exception.AccountLockedException;
import com.Bank_website.authserver.exception.DuplicateEmailException;
import com.Bank_website.authserver.exception.ForbiddenException;
import com.Bank_website.authserver.exception.InvalidCredentialsException;
import com.Bank_website.authserver.exception.RateLimitExceededException;
import com.Bank_website.authserver.exception.UnauthorizedException;
import com.Bank_website.authserver.exception.UserNotFoundException;
import com.Bank_website.authserver.repository.AuthUserRepository;
import com.Bank_website.authserver.repository.RefreshTokenRepository;
import com.Bank_website.authserver.security.AuthenticatedUser;
import com.Bank_website.authserver.service.AuthService;
import com.Bank_website.authserver.service.JwtService;
import com.Bank_website.authserver.service.RedisBlocklistService;
import com.Bank_website.authserver.service.RefreshTokenService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.security.KeyPairGenerator;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class AuthServiceTest {

    private static final String GOOD_PASSWORD = "Correct-Horse-9-Battery";
    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4); // fast for tests; production uses strength 12

    private final AuthUserRepository users = mock(AuthUserRepository.class);
    private final RefreshTokenRepository tokenRepo = mock(RefreshTokenRepository.class);
    private final RedisBlocklistService blocklist = mock(RedisBlocklistService.class);
    private final RedisTemplate<String, String> redis = mock(RedisTemplate.class);
    private final ValueOperations<String, String> ops = mock(ValueOperations.class);

    private JwtService jwt;
    private RefreshTokenService refreshService;
    private AuthService service;
    private final Map<String, RefreshToken> stored = new HashMap<>();
    private AuthUser alice;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        jwt = new JwtService(Base64.getEncoder().encodeToString(g.generateKeyPair().getPrivate().getEncoded()),
                "", "banking-auth", 900, blocklist);
        refreshService = new RefreshTokenService(tokenRepo, 604800);
        service = new AuthService(users, jwt, refreshService, encoder, redis, "boot-secret", 5, 15, 10);

        when(redis.opsForValue()).thenReturn(ops);
        when(ops.increment(any())).thenReturn(1L);
        when(tokenRepo.save(any(RefreshToken.class))).thenAnswer(i -> {
            RefreshToken t = i.getArgument(0);
            if (t.getTokenId() == null) t.setTokenId(UUID.randomUUID());
            stored.put(t.getTokenHash(), t);
            return t;
        });
        when(tokenRepo.findByTokenHash(any())).thenAnswer(i -> Optional.ofNullable(stored.get(i.getArgument(0))));
        when(users.save(any(AuthUser.class))).thenAnswer(i -> {
            AuthUser u = i.getArgument(0);
            if (u.getUserId() == null) u.setUserId(UUID.randomUUID());
            return u;
        });

        alice = new AuthUser();
        alice.setUserId(UUID.randomUUID());
        alice.setEmail("alice@example.com");
        alice.setPasswordHash(encoder.encode(GOOD_PASSWORD));
        alice.setRole("CUSTOMER");
        alice.setCustomerId(UUID.randomUUID());
        alice.setActive(true);
        when(users.findByEmail("alice@example.com")).thenReturn(Optional.of(alice));
        when(users.findById(alice.getUserId())).thenReturn(Optional.of(alice));
    }

    private RegisterRequest reg(String email, String role, UUID cid, UUID agent) {
        return new RegisterRequest(email, GOOD_PASSWORD, role, cid, agent);
    }

    private AuthenticatedUser staff() {
        return new AuthenticatedUser(UUID.randomUUID(), "STAFF", null, UUID.randomUUID());
    }

    private LoginRequest login(String password) {
        return new LoginRequest("alice@example.com", password);
    }

    // ------------------------------------------------------------ register

    @Test
    void staffRegistersACustomerWithABcryptHashAndNormalisedEmail() {
        UUID cid = UUID.randomUUID();
        var r = service.register(reg("New.User@Example.com", "CUSTOMER", cid, null), staff(), null);

        assertEquals("new.user@example.com", r.email());
        assertEquals("CUSTOMER", r.role());
        org.mockito.ArgumentCaptor<AuthUser> saved = org.mockito.ArgumentCaptor.forClass(AuthUser.class);
        verify(users).save(saved.capture());
        assertNotEquals(GOOD_PASSWORD, saved.getValue().getPasswordHash());
        assertTrue(encoder.matches(GOOD_PASSWORD, saved.getValue().getPasswordHash()));
        assertEquals(cid, saved.getValue().getCustomerId());
    }

    @Test
    void productionPasswordEncoderIsBcryptStrength12() {
        assertTrue(new SecurityConfig().passwordEncoder().encode("x").startsWith("$2a$12$"));
    }

    @Test
    void duplicateEmailIs409() {
        when(users.existsByEmail("alice@example.com")).thenReturn(true);
        assertThrows(DuplicateEmailException.class, () -> service.register(reg("alice@example.com", "CUSTOMER", null, null), staff(), null));
    }

    @Test
    void anonymousCallersCannotRegisterUsersExceptTheBootstrapStaff() {
        assertThrows(UnauthorizedException.class, () -> service.register(reg("c@example.com", "CUSTOMER", null, null), null, null));
        assertThrows(UnauthorizedException.class, () -> service.register(reg("s@example.com", "SYSTEM", null, null), null, "boot-secret"));
    }

    @Test
    void firstStaffNeedsTheBootstrapSecretAndOnlyWhileNoStaffExists() {
        assertThrows(ForbiddenException.class, () -> service.register(reg("s@example.com", "STAFF", null, null), null, "wrong"));
        assertThrows(ForbiddenException.class, () -> service.register(reg("s@example.com", "STAFF", null, null), null, null));
        assertEquals("STAFF", service.register(reg("s@example.com", "STAFF", null, null), null, "boot-secret").role());

        when(users.existsByRole("STAFF")).thenReturn(true);
        assertThrows(UnauthorizedException.class, () -> service.register(reg("s2@example.com", "STAFF", null, null), null, "boot-secret"));
    }

    @Test
    void nonStaffCallersAreForbidden() {
        AuthenticatedUser customer = new AuthenticatedUser(UUID.randomUUID(), "CUSTOMER", UUID.randomUUID(), null);
        assertThrows(ForbiddenException.class, () -> service.register(reg("x@example.com", "STAFF", null, null), customer, null));
    }

    // ------------------------------------------------------------ login

    @Test
    void successfulLoginReturnsBothTokensAndResetsTheCounters() {
        alice.setFailedLoginAttempts(3);
        LoginResponse r = service.login(login(GOOD_PASSWORD), "1.2.3.4");

        assertEquals("CUSTOMER", r.role());
        assertEquals(alice.getCustomerId(), r.customerId());
        assertNull(r.agentId());
        assertEquals(900, r.expiresIn());
        assertNotNull(r.refreshToken());
        assertEquals(0, alice.getFailedLoginAttempts());
        Claims c = jwt.validateToken(r.accessToken());
        assertEquals(alice.getUserId().toString(), c.getSubject());
        // refresh token is opaque (not a JWT), 43 chars, and only its hash is stored
        assertFalse(r.refreshToken().contains("."));
        assertEquals(43, r.refreshToken().length());
        assertTrue(stored.containsKey(refreshService.hash(r.refreshToken())));
        assertFalse(stored.containsKey(r.refreshToken()));
    }

    @Test
    void wrongPasswordAndUnknownEmailGiveTheSameGenericError() {
        InvalidCredentialsException wrong = assertThrows(InvalidCredentialsException.class, () -> service.login(login("Wrong-Password-1!"), "1.2.3.4"));
        InvalidCredentialsException unknown = assertThrows(InvalidCredentialsException.class,
                () -> service.login(new LoginRequest("ghost@example.com", GOOD_PASSWORD), "1.2.3.4"));
        assertEquals("Invalid credentials", wrong.getMessage());
        assertEquals(wrong.getMessage(), unknown.getMessage());
    }

    @Test
    void fifthWrongPasswordLocksTheAccountForFifteenMinutesAndTheSixthIsLocked() {
        for (int i = 1; i <= 4; i++) {
            assertThrows(InvalidCredentialsException.class, () -> service.login(login("Wrong-Password-1!"), "1.2.3.4"));
            assertEquals(i, alice.getFailedLoginAttempts());
        }
        AccountLockedException fifth = assertThrows(AccountLockedException.class, () -> service.login(login("Wrong-Password-1!"), "1.2.3.4"));
        assertEquals(900, fifth.getRetryAfterSeconds());
        assertNotNull(alice.getLockedUntil());
        assertTrue(alice.getLockedUntil().isAfter(OffsetDateTime.now().plusMinutes(14)));

        // even the correct password is refused while locked
        AccountLockedException sixth = assertThrows(AccountLockedException.class, () -> service.login(login(GOOD_PASSWORD), "1.2.3.4"));
        assertTrue(sixth.getRetryAfterSeconds() > 800 && sixth.getRetryAfterSeconds() <= 901);
    }

    @Test
    void anExpiredLockGivesAFreshSetOfAttempts() {
        alice.setFailedLoginAttempts(5);
        alice.setLockedUntil(OffsetDateTime.now().minusMinutes(1));
        assertNotNull(service.login(login(GOOD_PASSWORD), "1.2.3.4").accessToken());
        assertNull(alice.getLockedUntil());
        assertEquals(0, alice.getFailedLoginAttempts());
    }

    @Test
    void disabledAccountsGet403OnlyAfterAValidPassword() {
        alice.setActive(false);
        assertThrows(ForbiddenException.class, () -> service.login(login(GOOD_PASSWORD), "1.2.3.4"));
        assertThrows(InvalidCredentialsException.class, () -> service.login(login("Wrong-Password-1!"), "1.2.3.4"));
    }

    @Test
    void morethanTenAttemptsPerIpPerMinuteAre429() {
        when(ops.increment("login_rate:9.9.9.9")).thenReturn(11L);
        when(redis.getExpire("login_rate:9.9.9.9")).thenReturn(42L);
        RateLimitExceededException e = assertThrows(RateLimitExceededException.class, () -> service.login(login(GOOD_PASSWORD), "9.9.9.9"));
        assertEquals(42, e.getRetryAfterSeconds());
        verify(users, never()).findByEmail(any());

        when(ops.increment("login_rate:8.8.8.8")).thenReturn(10L); // the 10th is still allowed
        assertNotNull(service.login(login(GOOD_PASSWORD), "8.8.8.8"));
        when(ops.increment("login_rate:7.7.7.7")).thenReturn(1L);
        service.login(login(GOOD_PASSWORD), "7.7.7.7");
        verify(redis).expire(eq("login_rate:7.7.7.7"), any(java.time.Duration.class));
    }

    // ------------------------------------------------------------ refresh / logout

    @Test
    void refreshRotatesTheTokenAndTheOldOneIsDead() {
        LoginResponse first = service.login(login(GOOD_PASSWORD), "1.2.3.4");

        LoginResponse second = service.refresh(new RefreshRequest(first.refreshToken()));

        assertNotEquals(first.refreshToken(), second.refreshToken());
        assertNotNull(jwt.validateToken(second.accessToken()));
        assertTrue(stored.get(refreshService.hash(first.refreshToken())).isRevoked());
        assertFalse(stored.get(refreshService.hash(second.refreshToken())).isRevoked());

        // reuse of the old token: 401 and every session of the user is revoked
        assertThrows(InvalidCredentialsException.class, () -> service.refresh(new RefreshRequest(first.refreshToken())));
        verify(tokenRepo).revokeAllByUserId(alice.getUserId());
    }

    @Test
    void unknownExpiredAndInactiveUserRefreshesAre401() {
        assertThrows(InvalidCredentialsException.class, () -> service.refresh(new RefreshRequest("nope")));

        LoginResponse r = service.login(login(GOOD_PASSWORD), "1.2.3.4");
        stored.get(refreshService.hash(r.refreshToken())).setExpiresAt(OffsetDateTime.now().minusSeconds(1));
        assertThrows(InvalidCredentialsException.class, () -> service.refresh(new RefreshRequest(r.refreshToken())));

        LoginResponse r2 = service.login(login(GOOD_PASSWORD), "1.2.3.4");
        alice.setActive(false);
        assertThrows(InvalidCredentialsException.class, () -> service.refresh(new RefreshRequest(r2.refreshToken())));
    }

    @Test
    void refreshTokenLifetimeIsSevenDays() {
        LoginResponse r = service.login(login(GOOD_PASSWORD), "1.2.3.4");
        OffsetDateTime expires = stored.get(refreshService.hash(r.refreshToken())).getExpiresAt();
        assertTrue(expires.isAfter(OffsetDateTime.now().plusDays(7).minusMinutes(1)));
        assertTrue(expires.isBefore(OffsetDateTime.now().plusDays(7).plusMinutes(1)));
    }

    @Test
    void logoutRevokesTheRefreshTokenAndBlocklistsTheAccessJti() {
        LoginResponse r = service.login(login(GOOD_PASSWORD), "1.2.3.4");
        Claims claims = jwt.validateToken(r.accessToken());

        service.logout("Bearer " + r.accessToken(), new RefreshRequest(r.refreshToken()));

        assertTrue(stored.get(refreshService.hash(r.refreshToken())).isRevoked());
        org.mockito.ArgumentCaptor<Long> ttl = org.mockito.ArgumentCaptor.forClass(Long.class);
        verify(blocklist).blocklist(eq(claims.getId()), ttl.capture());
        assertTrue(ttl.getValue() > 880 && ttl.getValue() <= 900);
        assertThrows(InvalidCredentialsException.class, () -> service.refresh(new RefreshRequest(r.refreshToken())));
    }

    @Test
    void logoutNeedsAValidBearerAndCannotRevokeSomeoneElsesRefreshToken() {
        assertThrows(UnauthorizedException.class, () -> service.logout(null, new RefreshRequest("x")));
        assertThrows(UnauthorizedException.class, () -> service.logout("Bearer garbage", new RefreshRequest("x")));

        LoginResponse mine = service.login(login(GOOD_PASSWORD), "1.2.3.4");
        AuthUser bob = new AuthUser();
        bob.setUserId(UUID.randomUUID());
        bob.setRole("STAFF");
        String bobAccess = jwt.generateAccessToken(bob).token();
        service.logout("Bearer " + bobAccess, new RefreshRequest(mine.refreshToken()));
        assertFalse(stored.get(refreshService.hash(mine.refreshToken())).isRevoked());
    }

    // ------------------------------------------------------------ unlock

    @Test
    void unlockClearsTheLockAndTheCounter() {
        alice.setFailedLoginAttempts(5);
        alice.setLockedUntil(OffsetDateTime.now().plusMinutes(10));
        service.unlock(alice.getUserId());
        assertNull(alice.getLockedUntil());
        assertEquals(0, alice.getFailedLoginAttempts());
        assertThrows(UserNotFoundException.class, () -> service.unlock(UUID.randomUUID()));
    }

    @Test
    void passwordsNeverAppearInToString() {
        assertFalse(new RegisterRequest("a@b.co", "Secret-Password-1!", "STAFF", null, null).toString().contains("Secret"));
        assertFalse(new LoginRequest("a@b.co", "Secret-Password-1!").toString().contains("Secret"));
        assertFalse(new RefreshRequest("opaque-token-value").toString().contains("opaque"));
    }
}
