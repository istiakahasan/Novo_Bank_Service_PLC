package com.banking.security.util;

import com.banking.security.model.AuthenticatedUser;
import io.jsonwebtoken.Claims;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class RoleExtractor {

    public static final Set<String> ROLES = Set.of("CUSTOMER", "STAFF", "SYSTEM");

    private RoleExtractor() {
    }

    /** "CUSTOMER", "STAFF" or "SYSTEM" for an authenticated caller, otherwise null. */
    public static String roleOf(Authentication auth) {
        AuthenticatedUser user = userOf(auth);
        return user == null ? null : user.role();
    }

    public static UUID customerIdOf(Authentication auth) {
        AuthenticatedUser user = userOf(auth);
        return user == null ? null : user.customerId();
    }

    public static UUID agentIdOf(Authentication auth) {
        AuthenticatedUser user = userOf(auth);
        return user == null ? null : user.agentId();
    }

    public static AuthenticatedUser userOf(Authentication auth) {
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof AuthenticatedUser user) {
            return user;
        }
        return null;
    }

    /** ROLE_<role> authority for hasRole(...) / @PreAuthorize; an unknown role yields no authority at all. */
    public static List<GrantedAuthority> authoritiesFor(String role) {
        return role != null && ROLES.contains(role) ? List.of(new SimpleGrantedAuthority("ROLE_" + role)) : List.of();
    }

    /** Reads the "role" claim, falling back to the first entry of a "roles" list. Unknown values become null. */
    public static String roleFromClaims(Claims claims) {
        Object role = claims.get("role");
        if (role == null && claims.get("roles") instanceof Collection<?> roles && !roles.isEmpty()) {
            role = roles.iterator().next();
        }
        String text = role == null ? null : role.toString().toUpperCase();
        return text != null && ROLES.contains(text) ? text : null;
    }
}
