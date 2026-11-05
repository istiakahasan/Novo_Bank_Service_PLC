package com.Bank_website.Novo_Bank_Service_PLC.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;

public record CreateCustomerRequest(
        @NotBlank String firstName,
        @NotBlank String lastName,
        @NotNull @Past LocalDate dateOfBirth,
        @NotBlank @Pattern(regexp = "^(\\+880|01)[0-9]{9}$", message = "must match +880XXXXXXXXX or 01XXXXXXXXX") String mobileNo,
        @Email String email,
        @NotBlank @Pattern(regexp = "^(nid|passport|driving_license|birth_certificate)$",
                message = "must be one of nid, passport, driving_license, birth_certificate") String idType,
        @NotBlank String idNumber,
        @NotBlank String addressLine1
) {
}
