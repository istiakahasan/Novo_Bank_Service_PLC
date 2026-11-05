package com.Bank_website.auth.service;

import com.Bank_website.auth.dto.AuthDtos.CreateUserRequest;
import com.Bank_website.auth.dto.AuthDtos.TokenResponse;
import com.Bank_website.auth.dto.AuthDtos.UserResponse;
import com.Bank_website.auth.entity.AuthUser;
import com.Bank_website.auth.entity.RefreshToken;
import com.Bank_website.auth.repository.AuthUserRepository;
import com.Bank_website.auth.repository.RefreshTokenRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class AuthService {

    static final String REVOKED_PREFIX = "revoked:jti:";   // read by every service (security-starter)
    static final String LOGIN_FAIL_PREFIX = "login_fail:";

    private static final String DUMMY_HASH = new BCryptPasswordEncoder().encode("not-a-real-password");

    private final AuthUserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final TokenService tokens;
    private final StringRedisTemplate redis;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final SecureRandom random = new SecureRandom();
    private final long refreshTtlSeconds;
    private final int maxFailedLogins;

    public AuthService(AuthUserRepository users, RefreshTokenRepository refreshTokens, TokenService tokens,
                       StringRedisTemplate redis,
                       @Value("${auth.refresh-ttl-seconds}") long refreshTtlSeconds,
                       @Value("${auth.max-failed-logins:5}") int maxFailedLogins) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.tokens = tokens;
        this.redis = redis;
        this.refreshTtlSeconds = refreshTtlSeconds;
        this.maxFailedLogins = maxFailedLogins;
    }

    /** POST /auth/login */
    @Transactional
    public TokenResponse login(String username, String password) {
        String failKey = LOGIN_FAIL_PREFIX + username.toLowerCase();
        String failures = redis.opsForValue().get(failKey);
        if (failures != null && Integer.parseInt(failures) >= maxFailedLogins) {
            throw new AuthException(HttpStatus.TOO_MANY_REQUESTS, "Too many failed logins, try again in 15 minutes");
        }
        AuthUser user = users.findByUsername(username).orElse(null);
        // always run one BCrypt comparison so unknown users and wrong passwords take the same time
        boolean passwordOk = encoder.matches(password, user == null ? DUMMY_HASH : user.getPasswordHash());
        if (user == null || !passwordOk || !user.isEnabled()) {
            Long count = redis.opsForValue().increment(failKey);
            if (count != null && count == 1L) {
                redis.expire(failKey, Duration.ofMinutes(15));
            }
            throw new AuthException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }
        redis.delete(failKey);
        return issue(user);
    }

    /** POST /auth/refresh: single-use refresh tokens (rotation) with reuse detection. */
    @Transactional(noRollbackFor = AuthException.class)
    public TokenResponse refresh(String refreshToken) {
        RefreshToken stored = refreshTokens.findByTokenHash(hash(refreshToken))
                .orElseThrow(() -> new AuthException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));
        if (stored.isRevoked()) {
            // A used/revoked token came back: assume theft, end every session of this user.
            refreshTokens.revokeAllForUser(stored.getUserId());
            throw new AuthException(HttpStatus.UNAUTHORIZED, "Refresh token reuse detected, all sessions revoked");
        }
        if (stored.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "Refresh token expired");
        }
        AuthUser user = users.findById(stored.getUserId())
                .filter(AuthUser::isEnabled)
                .orElseThrow(() -> new AuthException(HttpStatus.UNAUTHORIZED, "User disabled"));
        stored.setRevoked(true);
        refreshTokens.save(stored);
        return issue(user);
    }

    /** POST /auth/logout: blocklists the access token's jti in Redis and revokes the refresh token. */
    @Transactional
    public void logout(Claims accessClaims, String refreshToken) {
        long secondsLeft = Duration.between(Instant.now(), accessClaims.getExpiration().toInstant()).getSeconds();
        if (secondsLeft > 0) {
            // key revoked:jti:{jti}, value 1, TTL = remaining life of the access token
            redis.opsForValue().set(REVOKED_PREFIX + accessClaims.getId(), "1", Duration.ofSeconds(secondsLeft));
        }
        refreshTokens.findByTokenHash(hash(refreshToken))
                .filter(t -> t.getUserId().toString().equals(accessClaims.getSubject()))
                .ifPresent(t -> {
                    t.setRevoked(true);
                    refreshTokens.save(t);
                });
    }

    /** Validates "Authorization: Bearer ..." issued by this service and not logged out. */
    public Claims authenticate(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "Bearer token required");
        }
        try {
            Claims claims = tokens.parseAccessToken(authorizationHeader.substring(7).trim());
            if (Boolean.TRUE.equals(redis.hasKey(REVOKED_PREFIX + claims.getId()))) {
                throw new AuthException(HttpStatus.UNAUTHORIZED, "Token revoked");
            }
            return claims;
        } catch (JwtException | IllegalArgumentException e) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "Invalid or expired token");
        }
    }

    /** POST /auth/users: STAFF only (checked by the controller). */
    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        if (users.existsByUsername(request.username())) {
            throw new AuthException(HttpStatus.CONFLICT, "Username already exists");
        }
        if ("CUSTOMER".equals(request.role()) && request.customerId() == null) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "customerId is required for role CUSTOMER");
        }
        if ("STAFF".equals(request.role()) && request.customerId() != null) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "customerId must be empty for role STAFF");
        }
        AuthUser user = new AuthUser();
        user.setId(UUID.randomUUID());
        user.setUsername(request.username());
        user.setPasswordHash(encoder.encode(request.password()));
        user.setRole(request.role());
        user.setCustomerId(request.customerId());
        user.setEnabled(true);
        user.setCreatedAt(OffsetDateTime.now());
        users.save(user);
        return new UserResponse(user.getId(), user.getUsername(), user.getRole(), user.getCustomerId());
    }

    private TokenResponse issue(AuthUser user) {
        byte[] raw = new byte[32];
        random.nextBytes(raw);
        String refreshToken = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);

        RefreshToken row = new RefreshToken();
        row.setId(UUID.randomUUID());
        row.setUserId(user.getId());
        row.setTokenHash(hash(refreshToken));
        row.setExpiresAt(OffsetDateTime.now().plusSeconds(refreshTtlSeconds));
        row.setRevoked(false);
        row.setCreatedAt(OffsetDateTime.now());
        refreshTokens.save(row);

        return new TokenResponse(tokens.createAccessToken(user), refreshToken, "Bearer", tokens.accessTtlSeconds());
    }

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
