package com.Bank_website.authserver.dto;

import java.util.UUID;

public record LoginResponse(
        String accessToken,
        String refreshToken,
        String role,
        UUID customerId,
        UUID agentId,
        long expiresIn
) {
    @Override
    public String toString() {
        return "LoginResponse[role=" + role + ", expiresIn=" + expiresIn + "]";
    }
}
