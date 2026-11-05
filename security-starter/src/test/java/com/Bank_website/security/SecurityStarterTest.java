package com.Bank_website.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityStarterTest {

    private static final KeyPair PAIR = newPair();
    private static final KeyPair OTHER = newPair();

    private static KeyPair newPair() {
        try {
            KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
            g.initialize(2048);
            return g.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private final JwtVerifier verifier =
            new JwtVerifier(List.of(PAIR.getPublic()), "novo-auth", new RoleExtractor());

    private String token(KeyPair signer, String issuer, long ttlSeconds, List<String> roles, UUID cid) {
        var b = Jwts.builder().issuer(issuer).subject("user-1").id(UUID.randomUUID().toString())
                .claim("roles", roles)
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plusSeconds(ttlSeconds)));
        if (cid != null) {
            b.claim("cid", cid.toString());
        }
        return b.signWith(signer.getPrivate(), Jwts.SIG.RS256).compact();
    }

    @Test
    void acceptsValidTokenAndExtractsClaims() {
        UUID cid = UUID.randomUUID();
        JwtVerifier.VerifiedToken t = verifier.verify(token(PAIR, "novo-auth", 300, List.of("CUSTOMER"), cid));
        assertEquals("user-1", t.principal().subject());
        assertEquals(cid, t.principal().customerId());
        assertTrue(t.principal().hasRole("CUSTOMER"));
    }

    @Test
    void rejectsExpiredWrongKeyWrongIssuerAndUnsigned() {
        assertThrows(JwtException.class, () -> verifier.verify(token(PAIR, "novo-auth", -3600, List.of("STAFF"), null)));
        assertThrows(JwtException.class, () -> verifier.verify(token(OTHER, "novo-auth", 300, List.of("STAFF"), null)));
        assertThrows(JwtException.class, () -> verifier.verify(token(PAIR, "someone-else", 300, List.of("STAFF"), null)));
        String unsigned = Jwts.builder().issuer("novo-auth").subject("x").claim("roles", List.of("STAFF"))
                .expiration(Date.from(Instant.now().plusSeconds(300))).compact();
        assertThrows(JwtException.class, () -> verifier.verify(unsigned));
    }

    @Test
    void rejectsTamperedPayload() {
        String valid = token(PAIR, "novo-auth", 300, List.of("CUSTOMER"), UUID.randomUUID());
        String[] parts = valid.split("\\.");
        String forged = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"iss\":\"novo-auth\",\"sub\":\"user-1\",\"roles\":[\"STAFF\"],\"exp\":9999999999}".getBytes(StandardCharsets.UTF_8));
        assertThrows(JwtException.class, () -> verifier.verify(parts[0] + "." + forged + "." + parts[2]));
    }

    @Test
    void rejectsHmacTokenSignedWithThePublicKeyBytes() {
        SecretKey hmac = io.jsonwebtoken.security.Keys.hmacShaKeyFor(PAIR.getPublic().getEncoded());
        String confused = Jwts.builder().issuer("novo-auth").subject("x").claim("roles", List.of("STAFF"))
                .expiration(Date.from(Instant.now().plusSeconds(300))).signWith(hmac).compact();
        assertThrows(RuntimeException.class, () -> verifier.verify(confused));
    }

    @Test
    void systemRoleInAUserTokenIsIgnored() {
        JwtVerifier.VerifiedToken t = verifier.verify(token(PAIR, "novo-auth", 300, List.of("SYSTEM", "CUSTOMER"), null));
        assertFalse(t.principal().hasRole("SYSTEM"));
        assertTrue(t.principal().hasRole("CUSTOMER"));
    }

    @Test
    void acceptsEitherKeyDuringRotation() {
        JwtVerifier rotating = new JwtVerifier(List.of(OTHER.getPublic(), PAIR.getPublic()), "novo-auth", new RoleExtractor());
        rotating.verify(token(PAIR, "novo-auth", 300, List.of("STAFF"), null));
        rotating.verify(token(OTHER, "novo-auth", 300, List.of("STAFF"), null));
    }

    @Test
    void parsesServiceKeysWithRotation() {
        Map<String, List<byte[]>> keys = SystemKeyAuthenticationFilter.parse("ledger=k1,k2; fraud-service=k3");
        assertEquals(2, keys.get("ledger").size());
        assertEquals(1, keys.get("fraud-service").size());
        assertTrue(SystemKeyAuthenticationFilter.parse("").isEmpty());
    }

    @Test
    void publicKeyParsingAcceptsBase64DerAndPem() {
        String der = Base64.getEncoder().encodeToString(PAIR.getPublic().getEncoded());
        assertEquals(PAIR.getPublic(), KeyParsing.rsaPublicKey(der));
        assertEquals(PAIR.getPublic(), KeyParsing.rsaPublicKey("-----BEGIN PUBLIC KEY-----\n" + der + "\n-----END PUBLIC KEY-----"));
        assertThrows(IllegalStateException.class, () -> KeyParsing.rsaPublicKey("not a key"));
    }
}
