package com.Bank_website.authserver.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 150) String email,
        @NotBlank
        @Size(min = 12, max = 72, message = "must be 12 to 72 characters")
        @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$",
                message = "must contain an upper-case letter, a lower-case letter, a digit and a symbol")
        String password,
        @NotBlank @Pattern(regexp = "^(CUSTOMER|STAFF|SYSTEM)$", message = "must be CUSTOMER, STAFF or SYSTEM") String role,
        UUID customerId,
        UUID agentId
) {
    /** The generated toString would print the password. */
    @Override
    public String toString() {
        return "RegisterRequest[email=" + email + ", role=" + role + ", customerId=" + customerId + ", agentId=" + agentId + "]";
    }
}
