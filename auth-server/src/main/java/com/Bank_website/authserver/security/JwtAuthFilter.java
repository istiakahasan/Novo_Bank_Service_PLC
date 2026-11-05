package com.Bank_website.authserver.security;

import com.Bank_website.authserver.exception.UnauthorizedException;
import com.Bank_website.authserver.service.JwtService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Authenticates "Authorization: Bearer <access token>" with this service's own JwtService (signature, issuer,
 * expiry, blocklist). No header = anonymous. A header that is present but invalid is a 401 immediately.
 * Created with `new` inside the security chain (not a bean) so it runs exactly once, inside Spring Security.
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final AuthenticationEntryPoint entryPoint;

    public JwtAuthFilter(JwtService jwtService, AuthenticationEntryPoint entryPoint) {
        this.jwtService = jwtService;
        this.entryPoint = entryPoint;
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
            Claims claims = jwtService.validateToken(header.substring(7).trim());
            AuthenticatedUser user = new AuthenticatedUser(
                    UUID.fromString(claims.getSubject()),
                    claims.get("role", String.class),
                    uuidClaim(claims, "customerId"),
                    uuidClaim(claims, "agentId"));
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    user, null, List.of(new SimpleGrantedAuthority("ROLE_" + user.role()))));
        } catch (UnauthorizedException | IllegalArgumentException e) {
            SecurityContextHolder.clearContext();
            entryPoint.commence(request, response, new BadCredentialsException("Invalid token"));
            return;
        }
        chain.doFilter(request, response);
    }

    private static UUID uuidClaim(Claims claims, String name) {
        String value = claims.get(name, String.class);
        return value == null ? null : UUID.fromString(value);
    }
}
