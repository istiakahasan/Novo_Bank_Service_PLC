package com.Bank_website.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Authenticates "Authorization: Bearer <access token>". No header = anonymous (the SecurityFilterChain
 * decides). A header that is present but invalid is rejected immediately with 401.
 * NOT a Spring bean on purpose: a Filter bean would also be auto-registered in the servlet container
 * and its OncePerRequestFilter guard would then skip it inside the security chain.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtVerifier verifier;
    private final RevocationChecker revocationChecker;
    private final RoleExtractor roleExtractor;
    private final AuthenticationEntryPoint unauthorized;

    public JwtAuthenticationFilter(JwtVerifier verifier, RevocationChecker revocationChecker,
                                   RoleExtractor roleExtractor, AuthenticationEntryPoint unauthorized) {
        this.verifier = verifier;
        this.revocationChecker = revocationChecker;
        this.roleExtractor = roleExtractor;
        this.unauthorized = unauthorized;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            chain.doFilter(request, response);
            return;
        }
        try {
            JwtVerifier.VerifiedToken token = verifier.verify(header.substring(7).trim());
            if (revocationChecker.isRevoked(token.jti())) {
                throw new BadCredentialsException("Token revoked");
            }
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    token.principal(), null, roleExtractor.authorities(token.principal().roles())));
        } catch (RuntimeException e) {
            SecurityContextHolder.clearContext();
            log.debug("Rejected bearer token: {}", e.getMessage());
            unauthorized.commence(request, response, new BadCredentialsException("Invalid token"));
            return;
        }
        chain.doFilter(request, response);
    }
}
