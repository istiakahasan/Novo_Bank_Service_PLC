package com.Bank_website.security;

import org.springframework.security.core.Authentication;

import java.util.Set;
import java.util.UUID;

/**
 * The authenticated caller. For users: subject = user id, customerId set for CUSTOMER.
 * For SYSTEM callers: subject = service id, roles = {SYSTEM}.
 */
public record AuthPrincipal(String subject, UUID customerId, Set<String> roles) {

    public static AuthPrincipal of(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof AuthPrincipal p) {
            return p;
        }
        return null;
    }

    public boolean hasRole(String role) {
        return roles.contains(role);
    }
}
