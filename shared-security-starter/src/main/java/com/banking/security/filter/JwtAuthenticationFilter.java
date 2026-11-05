package com.banking.security.filter;

import com.banking.security.handler.JsonErrorWriter;
import com.banking.security.model.AuthenticatedUser;
import com.banking.security.util.JwtValidator;
import com.banking.security.util.RoleExtractor;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * No Authorization header: continue (the endpoint rules decide). A Bearer token: validate it; valid -> an
 * AuthenticatedUser principal in the SecurityContext; invalid -> 401 JSON and the chain stops.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtValidator validator;

    public JwtAuthenticationFilter(JwtValidator validator) {
        this.validator = validator;
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
            Claims claims = validator.validate(header.substring(7).trim());
            AuthenticatedUser user = new AuthenticatedUser(
                    UUID.fromString(claims.getSubject()),
                    RoleExtractor.roleFromClaims(claims),
                    uuidClaim(claims, "customerId", "cid"),
                    uuidClaim(claims, "agentId", null));
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(user, null, RoleExtractor.authoritiesFor(user.role())));
        } catch (JwtException | IllegalArgumentException | NullPointerException e) {
            SecurityContextHolder.clearContext();
            log.debug("Rejected bearer token: {}", e.getMessage());
            JsonErrorWriter.write(response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid or expired token");
            return;
        }
        chain.doFilter(request, response);
    }

    private static UUID uuidClaim(Claims claims, String name, String fallback) {
        Object value = claims.get(name);
        if (value == null && fallback != null) {
            value = claims.get(fallback);
        }
        return value == null ? null : UUID.fromString(value.toString());
    }
}
