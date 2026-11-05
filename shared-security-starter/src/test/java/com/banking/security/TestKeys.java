package com.banking.security;

import io.jsonwebtoken.Jwts;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/** Shared helpers for the tests: RSA keys, tokens like the Auth Service issues, QStash-style signatures. */
public final class TestKeys {

    public static final KeyPair PAIR = newPair();
    public static final KeyPair OTHER = newPair();

    private TestKeys() {
    }

    public static KeyPair newPair() {
        try {
            KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
            g.initialize(2048);
            return g.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static String publicKeyBase64Der(KeyPair pair) {
        return Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
    }

    public static String token(KeyPair signer, String issuer, String role, UUID customerId, long ttlSeconds, String jti, String kid) {
        var builder = Jwts.builder().issuer(issuer).subject(UUID.randomUUID().toString())
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plusSeconds(ttlSeconds)));
        if (jti != null) {
            builder.id(jti);
        }
        if (kid != null) {
            builder.header().keyId(kid);
        }
        if (customerId != null) {
            builder.claim("customerId", customerId.toString());
        }
        return builder.signWith(signer.getPrivate(), Jwts.SIG.RS256).compact();
    }

    public static String token(String role, UUID customerId) {
        return token(PAIR, "banking-auth", role, customerId, 900, UUID.randomUUID().toString(), "v1");
    }

    public static String modulusJwks(KeyPair pair, String kid) {
        RSAPublicKey key = (RSAPublicKey) pair.getPublic();
        Base64.Encoder enc = Base64.getUrlEncoder().withoutPadding();
        byte[] n = key.getModulus().toByteArray();
        if (n[0] == 0) {
            n = java.util.Arrays.copyOfRange(n, 1, n.length);
        }
        return "{\"keys\":[{\"kty\":\"RSA\",\"kid\":\"" + kid + "\",\"use\":\"sig\",\"alg\":\"RS256\",\"n\":\""
                + enc.encodeToString(n) + "\",\"e\":\"" + enc.encodeToString(key.getPublicExponent().toByteArray()) + "\"}]}";
    }

    // ---- QStash signatures

    public static String hmacV1(String key, byte[] body) throws Exception {
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(key.getBytes(), "HmacSHA256"));
        return "v1=" + Base64.getEncoder().encodeToString(mac.doFinal(body));
    }

    /** The real Upstash-Signature: HS256 JWT with iss=Upstash and body=base64url(sha256(body)). */
    public static String upstashJwt(String key, byte[] body, long expEpoch) throws Exception {
        Base64.Encoder enc = Base64.getUrlEncoder().withoutPadding();
        String header = enc.encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes());
        String bodyHash = enc.encodeToString(MessageDigest.getInstance("SHA-256").digest(body));
        long now = Instant.now().getEpochSecond();
        String claims = enc.encodeToString(("{\"iss\":\"Upstash\",\"sub\":\"https://x\",\"exp\":" + expEpoch + ",\"nbf\":" + now
                + ",\"iat\":" + now + ",\"jti\":\"j\",\"body\":\"" + bodyHash + "\"}").getBytes());
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(key.getBytes(), "HmacSHA256"));
        return header + "." + claims + "." + enc.encodeToString(mac.doFinal((header + "." + claims).getBytes()));
    }

    public static List<String> keysFor(String current, String next) {
        return List.of(current, next);
    }
}
