package com.Bank_website.authserver.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of POST /api/auth/refresh and POST /api/auth/logout. */
public record RefreshRequest(@NotBlank @Size(max = 200) String refreshToken) {

    @Override
    public String toString() {
        return "RefreshRequest[refreshToken=***]";
    }
}
