package com.Bank_website.security;

import io.jsonwebtoken.Claims;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Turns the "roles" claim into authorities. Only CUSTOMER and STAFF may come from a user JWT:
 * SYSTEM is granted exclusively by a valid service key, so a forged or misissued token can never be SYSTEM.
 */
public class RoleExtractor {

    public static final String CUSTOMER = "CUSTOMER";
    public static final String STAFF = "STAFF";
    public static final String SYSTEM = "SYSTEM";
    private static final Set<String> USER_ROLES = Set.of(CUSTOMER, STAFF);

    public Set<String> userRoles(Claims claims) {
        Set<String> roles = new HashSet<>();
        Object claim = claims.get("roles");
        if (claim instanceof Collection<?> values) {
            for (Object value : values) {
                String role = String.valueOf(value).toUpperCase(Locale.ROOT);
                if (USER_ROLES.contains(role)) {
                    roles.add(role);
                }
            }
        }
        return roles;
    }

    public List<GrantedAuthority> authorities(Collection<String> roles) {
        return roles.stream().<GrantedAuthority>map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList();
    }
}
