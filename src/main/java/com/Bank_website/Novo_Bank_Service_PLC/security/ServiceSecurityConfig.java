package com.Bank_website.Novo_Bank_Service_PLC.security;

import com.Bank_website.security.SecurityBaseline;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Customer + Ledger authorization. Layer 1 (here): role per route. Layer 2 (@PreAuthorize on the
 * controllers): ownership, e.g. a CUSTOMER may only read their own account.
 * SYSTEM (service key) may only call the customer lookups and the AML-risk update.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class ServiceSecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(org.springframework.security.config.annotation.web.builders.HttpSecurity http,
                                                   SecurityBaseline baseline) throws Exception {
        baseline.apply(http).authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                // customer service
                .requestMatchers(HttpMethod.POST, "/api/customers").hasRole("STAFF")
                .requestMatchers(HttpMethod.GET, "/api/customers").hasRole("STAFF")
                .requestMatchers(HttpMethod.GET, "/api/customers/me").hasRole("CUSTOMER")
                .requestMatchers(HttpMethod.GET, "/api/customers/*/accounts").hasAnyRole("CUSTOMER", "STAFF")
                .requestMatchers(HttpMethod.PATCH, "/api/customers/*/kyc").hasRole("STAFF")
                .requestMatchers(HttpMethod.PATCH, "/api/customers/*/aml-risk").hasAnyRole("STAFF", "SYSTEM")
                .requestMatchers(HttpMethod.GET, "/api/customers/by-mobile/*").hasAnyRole("STAFF", "SYSTEM")
                .requestMatchers(HttpMethod.GET, "/api/customers/by-account/*").hasAnyRole("CUSTOMER", "STAFF", "SYSTEM")
                .requestMatchers(HttpMethod.GET, "/api/customers/*").hasAnyRole("CUSTOMER", "STAFF", "SYSTEM")
                // ledger: aggregate deposit summary for Treasury and Staff
                .requestMatchers(HttpMethod.GET, "/api/accounts/summary").hasAnyRole("STAFF", "SYSTEM")
                // ledger: GET /api/accounts?customerId= is the one SYSTEM-only call (used by the Customer Service)
                .requestMatchers(HttpMethod.GET, "/api/accounts").hasRole("SYSTEM")
                // ledger: everything else is for users only, never SYSTEM
                .requestMatchers("/api/transfers/**", "/api/accounts/**").hasAnyRole("CUSTOMER", "STAFF")
                .anyRequest().denyAll());
        return http.build();
    }
}
