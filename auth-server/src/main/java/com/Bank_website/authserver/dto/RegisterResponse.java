package com.Bank_website.authserver.dto;

import java.util.UUID;

public record RegisterResponse(UUID userId, String email, String role) {
}
