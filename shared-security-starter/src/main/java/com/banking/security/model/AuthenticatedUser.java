package com.banking.security.model;

import java.util.UUID;

/** The caller, as it is placed in the SecurityContext principal after a token was validated. */
public record AuthenticatedUser(UUID userId, String role, UUID customerId, UUID agentId) {
}
