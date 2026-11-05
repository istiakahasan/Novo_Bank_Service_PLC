package com.Bank_website.authserver.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        // "username" is accepted as an alias so the customer portal / agent console login form works unchanged
        @JsonAlias("username") @NotBlank @Size(max = 150) String email,
        @NotBlank @Size(max = 200) String password
) {
    /** The generated toString would print the password. */
    @Override
    public String toString() {
        return "LoginRequest[email=" + email + "]";
    }
}
