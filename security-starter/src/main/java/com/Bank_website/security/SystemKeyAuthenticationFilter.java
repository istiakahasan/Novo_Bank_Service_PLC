package com.Bank_website.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Service-to-service authentication, separate from user JWTs.
 * Caller sends  X-Service-Id: ledger   and   X-Service-Key: <secret>.
 * Inbound keys come from security.system.keys ("ledger=k1,k2;fraud-service=k3"); several keys per id
 * allow zero-downtime rotation. A valid pair yields ROLE_SYSTEM; a wrong pair is a 401.
 * NOT a Spring bean (see JwtAuthenticationFilter).
 */
public class SystemKeyAuthenticationFilter extends OncePerRequestFilter {

    public static final String ID_HEADER = "X-Service-Id";
    public static final String KEY_HEADER = "X-Service-Key";
    private static final Logger log = LoggerFactory.getLogger(SystemKeyAuthenticationFilter.class);

    private final Map<String, List<byte[]>> keysByService;
    private final AuthenticationEntryPoint unauthorized;

    public SystemKeyAuthenticationFilter(String configuredKeys, AuthenticationEntryPoint unauthorized) {
        this.keysByService = parse(configuredKeys);
        this.unauthorized = unauthorized;
    }

    static Map<String, List<byte[]>> parse(String configured) {
        Map<String, List<byte[]>> result = new HashMap<>();
        if (configured == null || configured.isBlank()) {
            return result;
        }
        for (String entry : configured.split(";")) {
            String[] idAndKeys = entry.trim().split("=", 2);
            if (idAndKeys.length != 2 || idAndKeys[0].isBlank()) {
                continue;
            }
            List<byte[]> keys = new ArrayList<>();
            for (String key : idAndKeys[1].split(",")) {
                if (!key.isBlank()) {
                    keys.add(key.trim().getBytes(StandardCharsets.UTF_8));
                }
            }
            result.put(idAndKeys[0].trim(), keys);
        }
        return result;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String serviceId = request.getHeader(ID_HEADER);
        String serviceKey = request.getHeader(KEY_HEADER);
        if (serviceId == null && serviceKey == null) {
            chain.doFilter(request, response);
            return;
        }
        if (!matches(serviceId, serviceKey)) {
            SecurityContextHolder.clearContext();
            log.warn("Rejected service credentials for id='{}'", serviceId);
            unauthorized.commence(request, response, new BadCredentialsException("Invalid service credentials"));
            return;
        }
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new AuthPrincipal(serviceId, null, Set.of(RoleExtractor.SYSTEM)), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + RoleExtractor.SYSTEM))));
        chain.doFilter(request, response);
    }

    private boolean matches(String serviceId, String serviceKey) {
        if (serviceId == null || serviceKey == null) {
            return false;
        }
        List<byte[]> keys = keysByService.get(serviceId);
        if (keys == null) {
            return false;
        }
        byte[] presented = serviceKey.getBytes(StandardCharsets.UTF_8);
        boolean ok = false;
        for (byte[] key : keys) {
            ok |= MessageDigest.isEqual(key, presented); // constant-time, no early exit
        }
        return ok;
    }
}
