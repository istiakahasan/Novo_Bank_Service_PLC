package com.Bank_website.authserver.service;

import com.Bank_website.authserver.dto.LoginRequest;
import com.Bank_website.authserver.dto.LoginResponse;
import com.Bank_website.authserver.dto.RefreshRequest;
import com.Bank_website.authserver.dto.RegisterRequest;
import com.Bank_website.authserver.dto.RegisterResponse;
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
import com.Bank_website.authserver.security.AuthenticatedUser;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;

@Service
public class AuthService {

    private final AuthUserRepository users;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final RedisTemplate<String, String> redis;
    private final String bootstrapSecret;
    private final int maxFailedAttempts;
    private final long lockMinutes;
    private final int loginRateLimit;
    /** Compared against when the email is unknown, so unknown-user and wrong-password take the same time. */
    private final String dummyHash;

    public AuthService(AuthUserRepository users, JwtService jwtService, RefreshTokenService refreshTokens,
                       PasswordEncoder passwordEncoder, RedisTemplate<String, String> redis,
                       @Value("${auth.bootstrap-secret:}") String bootstrapSecret,
                       @Value("${auth.max-failed-attempts:5}") int maxFailedAttempts,
                       @Value("${auth.lock-minutes:15}") long lockMinutes,
                       @Value("${auth.login-rate-limit-per-minute:10}") int loginRateLimit) {
        this.users = users;
        this.jwtService = jwtService;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.redis = redis;
        this.bootstrapSecret = bootstrapSecret;
        this.maxFailedAttempts = maxFailedAttempts;
        this.lockMinutes = lockMinutes;
        this.loginRateLimit = loginRateLimit;
        this.dummyHash = passwordEncoder.encode("not-a-real-password");
    }

    /**
     * POST /api/auth/register.
     * Only a STAFF caller may create users (any role, with customerId / agentId). The single exception is the very
     * first STAFF user, who may be created without a token while no STAFF exists, and only with the bootstrap secret.
     */
    @Transactional
    public RegisterResponse register(RegisterRequest request, AuthenticatedUser caller, String providedBootstrapSecret) {
        if (caller == null) {
            boolean bootstrap = "STAFF".equals(request.role()) && !users.existsByRole("STAFF");
            if (!bootstrap) {
                throw new UnauthorizedException("Authentication required");
            }
            if (bootstrapSecret.isBlank() || !constantTimeEquals(bootstrapSecret, providedBootstrapSecret)) {
                throw new ForbiddenException("Bootstrap secret required to create the first STAFF user");
            }
        } else if (!caller.isStaff()) {
            throw new ForbiddenException("Only STAFF can create users");
        }

        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmail(email)) {
            throw new DuplicateEmailException();
        }
        AuthUser user = new AuthUser();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(request.role());
        user.setCustomerId(request.customerId());
        user.setAgentId(request.agentId());
        user.setActive(true);
        AuthUser saved = users.save(user);
        return new RegisterResponse(saved.getUserId(), saved.getEmail(), saved.getRole());
    }

    /** POST /api/auth/login. Failed-attempt counters must survive the exception, hence noRollbackFor. */
    @Transactional(noRollbackFor = {InvalidCredentialsException.class, AccountLockedException.class, ForbiddenException.class})
    public LoginResponse login(LoginRequest request, String clientIp) {
        enforceRateLimit(clientIp);

        AuthUser user = users.findByEmail(request.email().trim().toLowerCase(Locale.ROOT)).orElse(null);
        if (user == null) {
            passwordEncoder.matches(request.password(), dummyHash);
            throw new InvalidCredentialsException();
        }

        OffsetDateTime now = OffsetDateTime.now();
        if (user.getLockedUntil() != null) {
            if (user.getLockedUntil().isAfter(now)) {
                throw new AccountLockedException(Duration.between(now, user.getLockedUntil()).toSeconds() + 1);
            }
            user.setLockedUntil(null);           // lock expired: a fresh set of attempts
            user.setFailedLoginAttempts(0);
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            int attempts = user.getFailedLoginAttempts() + 1;
            user.setFailedLoginAttempts(attempts);
            if (attempts >= maxFailedAttempts) {
                user.setLockedUntil(now.plusMinutes(lockMinutes));
                users.save(user);
                throw new AccountLockedException(lockMinutes * 60);
            }
            users.save(user);
            throw new InvalidCredentialsException();
        }

        if (!user.isActive()) {
            throw new ForbiddenException("Account is disabled");
        }

        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        users.save(user);
        return tokensFor(user, refreshTokens.issue(user.getUserId()));
    }

    /** POST /api/auth/refresh: single-use refresh tokens. Reuse revokes every session, so no rollback on the 401. */
    @Transactional(noRollbackFor = InvalidCredentialsException.class)
    public LoginResponse refresh(RefreshRequest request) {
        RefreshToken old = refreshTokens.requireValid(request.refreshToken());
        AuthUser user = users.findById(old.getUserId()).filter(AuthUser::isActive).orElseThrow(InvalidCredentialsException::new);
        return tokensFor(user, refreshTokens.rotate(old));
    }

    /** POST /api/auth/logout: revokes the refresh token and blocklists the access token's jti until it expires. */
    @Transactional
    public void logout(String authorizationHeader, RefreshRequest request) {
        if (authorizationHeader == null || !authorizationHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
            throw new UnauthorizedException("Bearer token required");
        }
        Claims claims = jwtService.validateToken(authorizationHeader.substring(7).trim());
        refreshTokens.revoke(request.refreshToken(), UUID.fromString(claims.getSubject()));
        long secondsLeft = Duration.between(Instant.now(), claims.getExpiration().toInstant()).getSeconds();
        jwtService.addToBlocklist(jwtService.getJti(claims), secondsLeft);
    }

    /** POST /api/auth/admin/unlock/{userId} (STAFF; enforced by the controller and the security chain). */
    @Transactional
    public void unlock(UUID userId) {
        AuthUser user = users.findById(userId).orElseThrow(UserNotFoundException::new);
        user.setLockedUntil(null);
        user.setFailedLoginAttempts(0);
        users.save(user);
    }

    // ------------------------------------------------------------------ helpers

    private LoginResponse tokensFor(AuthUser user, String refreshToken) {
        JwtService.IssuedToken access = jwtService.generateAccessToken(user);
        return new LoginResponse(access.token(), refreshToken, user.getRole(), user.getCustomerId(), user.getAgentId(),
                jwtService.accessTtlSeconds());
    }

    /** 10 login attempts per IP per minute: Redis key login_rate:{ip}, fixed one-minute window. */
    private void enforceRateLimit(String clientIp) {
        String key = "login_rate:" + clientIp;
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redis.expire(key, Duration.ofSeconds(60));
        }
        if (count != null && count > loginRateLimit) {
            Long ttl = redis.getExpire(key);
            throw new RateLimitExceededException(ttl == null || ttl < 0 ? 60 : ttl);
        }
    }

    private static boolean constantTimeEquals(String expected, String provided) {
        if (provided == null) {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), provided.getBytes(StandardCharsets.UTF_8));
    }
}
