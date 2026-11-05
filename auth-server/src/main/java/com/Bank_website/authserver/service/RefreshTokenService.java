package com.Bank_website.authserver.service;

import com.Bank_website.authserver.entity.RefreshToken;
import com.Bank_website.authserver.exception.InvalidCredentialsException;
import com.Bank_website.authserver.repository.RefreshTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/** Opaque refresh tokens (never JWTs, so they can be revoked), stored only as SHA-256 hashes. */
@Component
public class RefreshTokenService {

    private final RefreshTokenRepository repository;
    private final long ttlSeconds;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository repository, @Value("${jwt.refresh-ttl-seconds}") long ttlSeconds) {
        this.repository = repository;
        this.ttlSeconds = ttlSeconds;
    }

    /** 32 random bytes, base64url (43 characters). */
    public String generateOpaqueToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Creates and stores a new token for the user; returns the raw value (shown to the client once). */
    public String issue(UUID userId) {
        String raw = generateOpaqueToken();
        RefreshToken row = new RefreshToken();
        row.setUserId(userId);
        row.setTokenHash(hash(raw));
        row.setExpiresAt(OffsetDateTime.now().plusSeconds(ttlSeconds));
        row.setRevoked(false);
        repository.save(row);
        return raw;
    }

    /**
     * Looks the token up. Unknown, expired or revoked -> InvalidCredentialsException (401).
     * A revoked token coming back is treated as theft: every session of that user is revoked.
     */
    public RefreshToken requireValid(String raw) {
        RefreshToken token = repository.findByTokenHash(hash(raw)).orElseThrow(InvalidCredentialsException::new);
        if (token.isRevoked()) {
            repository.revokeAllByUserId(token.getUserId());
            throw new InvalidCredentialsException();
        }
        if (token.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new InvalidCredentialsException();
        }
        return token;
    }

    /** Rotation: the presented token becomes unusable and a fresh one is returned. */
    public String rotate(RefreshToken old) {
        old.setRevoked(true);
        repository.save(old);
        return issue(old.getUserId());
    }

    /** Logout: revokes the token only if it belongs to the user that is logging out. */
    public void revoke(String raw, UUID expectedUserId) {
        repository.findByTokenHash(hash(raw))
                .filter(t -> t.getUserId().equals(expectedUserId))
                .ifPresent(t -> {
                    t.setRevoked(true);
                    repository.save(t);
                });
    }
}
