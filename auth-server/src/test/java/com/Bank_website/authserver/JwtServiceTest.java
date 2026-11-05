package com.Bank_website.authserver;

import com.Bank_website.authserver.entity.AuthUser;
import com.Bank_website.authserver.exception.UnauthorizedException;
import com.Bank_website.authserver.service.JwtService;
import com.Bank_website.authserver.service.RedisBlocklistService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class JwtServiceTest {

    private static final KeyPair PAIR = newPair();

    private static KeyPair newPair() {
        try {
            KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
            g.initialize(2048);
            return g.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static final String PKCS8_B64 = Base64.getEncoder().encodeToString(PAIR.getPrivate().getEncoded());
    private final RedisBlocklistService blocklist = mock(RedisBlocklistService.class);
    private final JwtService jwt = new JwtService(PKCS8_B64, "", "banking-auth", 900, blocklist);

    private AuthUser user(String role, UUID customerId, UUID agentId) {
        AuthUser u = new AuthUser();
        u.setUserId(UUID.randomUUID());
        u.setEmail("a@b.co");
        u.setRole(role);
        u.setCustomerId(customerId);
        u.setAgentId(agentId);
        return u;
    }

    @Test
    void accessTokenHasTheSpecifiedClaimsAndExactly15MinuteLifetime() {
        UUID cid = UUID.randomUUID();
        AuthUser u = user("CUSTOMER", cid, null);
        JwtService.IssuedToken issued = jwt.generateAccessToken(u);

        Claims c = jwt.validateToken(issued.token());

        assertEquals(u.getUserId().toString(), c.getSubject());
        assertEquals("banking-auth", c.getIssuer());
        assertEquals("CUSTOMER", c.get("role"));
        assertEquals(cid.toString(), c.get("customerId"));
        assertEquals(900_000L, c.getExpiration().getTime() - c.getIssuedAt().getTime());
        assertEquals(issued.jti(), jwt.getJti(c));
        assertNotNull(UUID.fromString(c.getId()));
        // names read by the shared security-starter
        assertEquals(List.of("CUSTOMER"), c.get("roles"));
        assertEquals(cid.toString(), c.get("cid"));
    }

    @Test
    void staffTokenCarriesAgentIdAndNoCustomerId() {
        UUID agent = UUID.randomUUID();
        Claims c = jwt.validateToken(jwt.generateAccessToken(user("STAFF", null, agent)).token());
        assertEquals(agent.toString(), c.get("agentId"));
        assertEquals(null, c.get("customerId"));
    }

    @Test
    void tamperedExpiredForeignAndBlocklistedTokensAreRejected() {
        String token = jwt.generateAccessToken(user("CUSTOMER", UUID.randomUUID(), null)).token();
        String[] parts = token.split("\\.");
        String forgedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"iss\":\"banking-auth\",\"sub\":\"x\",\"role\":\"STAFF\",\"exp\":9999999999}".getBytes());
        assertThrows(UnauthorizedException.class, () -> jwt.validateToken(parts[0] + "." + forgedPayload + "." + parts[2]));

        JwtService otherIssuer = new JwtService(PKCS8_B64, "", "someone-else", 900, blocklist);
        assertThrows(UnauthorizedException.class, () -> jwt.validateToken(otherIssuer.generateAccessToken(user("STAFF", null, null)).token()));

        JwtService expired = new JwtService(PKCS8_B64, "", "banking-auth", -3600, blocklist);
        assertThrows(UnauthorizedException.class, () -> jwt.validateToken(expired.generateAccessToken(user("STAFF", null, null)).token()));

        assertThrows(UnauthorizedException.class, () -> jwt.validateToken("not.a.jwt"));

        when(blocklist.isBlocklisted(any())).thenReturn(true);
        assertThrows(UnauthorizedException.class, () -> jwt.validateToken(token));
    }

    @Test
    void jwksPublishesTheSigningKey() {
        List<Map<String, Object>> keys = (List<Map<String, Object>>) jwt.jwks().get("keys");
        Map<String, Object> jwk = keys.get(0);
        assertEquals("RSA", jwk.get("kty"));
        assertEquals("v1", jwk.get("kid"));
        assertEquals("sig", jwk.get("use"));
        assertEquals("RS256", jwk.get("alg"));
        assertEquals("AQAB", jwk.get("e"));
        BigInteger n = new BigInteger(1, Base64.getUrlDecoder().decode((String) jwk.get("n")));
        assertEquals(((RSAPublicKey) PAIR.getPublic()).getModulus(), n);
    }

    @Test
    void privateKeyIsAcceptedInEveryCommonFormat() {
        byte[] pkcs8 = PAIR.getPrivate().getEncoded();
        byte[] pkcs1 = java.util.Arrays.copyOfRange(pkcs8, 26, pkcs8.length); // strip the 26-byte PKCS#8 header of a 2048-bit RSA key
        String body8 = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(pkcs8);
        String body1 = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(pkcs1);
        String pemPkcs8 = "-----BEGIN PRIVATE KEY-----\n" + body8 + "\n-----END PRIVATE KEY-----\n";
        String pemPkcs1 = "-----BEGIN RSA PRIVATE KEY-----\n" + body1 + "\n-----END RSA PRIVATE KEY-----\n";

        for (String format : List.of(PKCS8_B64, pemPkcs8, Base64.getEncoder().encodeToString(pemPkcs8.getBytes()),
                pemPkcs1, Base64.getEncoder().encodeToString(pemPkcs1.getBytes()))) {
            JwtService candidate = new JwtService(format, "", "banking-auth", 900, blocklist);
            // a token signed with this key verifies with the original public key
            candidate.generateAccessToken(user("STAFF", null, null));
            assertEquals(((RSAPublicKey) PAIR.getPublic()).getModulus(),
                    new BigInteger(1, Base64.getUrlDecoder().decode((String) ((List<Map<String, Object>>) candidate.jwks().get("keys")).get(0).get("n"))));
        }
        assertThrows(IllegalStateException.class, () -> new JwtService("garbage", "", "banking-auth", 900, blocklist));
    }

    @Test
    void aPublicKeyThatDoesNotBelongToThePrivateKeyFailsStartup() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        String other = Base64.getEncoder().encodeToString(g.generateKeyPair().getPublic().getEncoded());
        assertThrows(IllegalStateException.class, () -> new JwtService(PKCS8_B64, other, "banking-auth", 900, blocklist));
        String matching = Base64.getEncoder().encodeToString(PAIR.getPublic().getEncoded());
        assertTrue(new JwtService(PKCS8_B64, matching, "banking-auth", 900, blocklist).jwks().containsKey("keys"));
    }

    @Test
    void blocklistWritesBothKeysWithTheRemainingLifetime() {
        RedisTemplate<String, String> redis = mock(RedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        RedisBlocklistService service = new RedisBlocklistService(redis);

        service.blocklist("jti-1", 600);
        verify(ops).set("jwt_blocklist:jti-1", "1", Duration.ofSeconds(600));
        verify(ops).set("revoked:jti:jti-1", "1", Duration.ofSeconds(600));

        service.blocklist("jti-2", 0); // already expired: nothing to store
        verify(ops, never()).set(eq("jwt_blocklist:jti-2"), any(), any(Duration.class));

        when(redis.hasKey("jwt_blocklist:jti-1")).thenReturn(true);
        assertTrue(service.isBlocklisted("jti-1"));
        assertEquals(false, service.isBlocklisted("jti-3"));
    }
}
