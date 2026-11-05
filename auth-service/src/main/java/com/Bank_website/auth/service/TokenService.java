package com.Bank_website.auth.service;

import com.Bank_website.auth.entity.AuthUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/** Issues and parses RS256 access tokens. The private key never leaves this service. */
@Service
public class TokenService {

    private final PrivateKey privateKey;
    private final PublicKey publicKey;
    private final String issuer;
    private final long accessTtlSeconds;

    public TokenService(@Value("${auth.private-key}") String privateKeyEncoded,
                        @Value("${auth.issuer}") String issuer,
                        @Value("${auth.access-ttl-seconds}") long accessTtlSeconds) {
        try {
            String base64 = privateKeyEncoded.replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", "");
            KeyFactory factory = KeyFactory.getInstance("RSA");
            this.privateKey = factory.generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)));
            RSAPrivateCrtKey crt = (RSAPrivateCrtKey) this.privateKey;
            this.publicKey = factory.generatePublic(new RSAPublicKeySpec(crt.getModulus(), crt.getPublicExponent()));
        } catch (GeneralSecurityException | IllegalArgumentException | ClassCastException e) {
            throw new IllegalStateException("auth.private-key is not a valid PKCS#8 RSA private key", e);
        }
        this.issuer = issuer;
        this.accessTtlSeconds = accessTtlSeconds;
    }

    public String createAccessToken(AuthUser user) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .issuer(issuer)
                .subject(user.getId().toString())
                .id(UUID.randomUUID().toString())
                .claim("roles", List.of(user.getRole()))
                .claim("username", user.getUsername())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(accessTtlSeconds)));
        if (user.getCustomerId() != null) {
            builder.claim("cid", user.getCustomerId().toString());
        }
        return builder.signWith(privateKey, Jwts.SIG.RS256).compact();
    }

    /** @throws io.jsonwebtoken.JwtException on bad signature, expiry, wrong issuer */
    public Claims parseAccessToken(String token) {
        return Jwts.parser().verifyWith(publicKey).requireIssuer(issuer).clockSkewSeconds(30)
                .build().parseSignedClaims(token).getPayload();
    }

    public String publicKeyBase64() {
        return Base64.getEncoder().encodeToString(publicKey.getEncoded());
    }

    public long accessTtlSeconds() {
        return accessTtlSeconds;
    }
}
