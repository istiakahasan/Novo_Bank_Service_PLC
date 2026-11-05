package com.Bank_website.authserver.security;

import java.util.UUID;

/** The caller as seen by this service after the bearer token was validated. */
public record AuthenticatedUser(UUID userId, String role, UUID customerId, UUID agentId) {

    public boolean isStaff() {
        return "STAFF".equals(role);
    }
}
