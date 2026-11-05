package com.Bank_website.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.SignatureException;

import java.security.PublicKey;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Verifies RS256 access tokens locally with cached public keys: no call to the auth server per request.
 * Rejects: bad signature, wrong issuer, expired, unsigned ("alg":"none") and HMAC-confusion tokens.
 */
public class JwtVerifier {

    public record VerifiedToken(AuthPrincipal principal, String jti, Instant expiresAt) {
    }

    private final List<PublicKey> keys;
    private final String issuer;
    private final RoleExtractor roleExtractor;

    public JwtVerifier(List<PublicKey> keys, String issuer, RoleExtractor roleExtractor) {
        this.keys = keys;
        this.issuer = issuer;
        this.roleExtractor = roleExtractor;
    }

    public VerifiedToken verify(String token) {
        if (keys.isEmpty()) {
            throw new JwtException("No verification key configured");
        }
        JwtException signatureFailure = null;
        for (PublicKey key : keys) {
            try {
                Claims claims = Jwts.parser()
                        .verifyWith(key)
                        .requireIssuer(issuer)
                        .clockSkewSeconds(30)
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();
                return toVerified(claims);
            } catch (SignatureException e) {
                signatureFailure = e; // maybe signed with the other (rotation) key
            }
        }
        throw signatureFailure;
    }

    private VerifiedToken toVerified(Claims claims) {
        Object cid = claims.get("cid");
        UUID customerId = cid == null ? null : UUID.fromString(cid.toString());
        AuthPrincipal principal = new AuthPrincipal(claims.getSubject(), customerId, roleExtractor.userRoles(claims));
        return new VerifiedToken(principal, claims.getId(), claims.getExpiration().toInstant());
    }
}
