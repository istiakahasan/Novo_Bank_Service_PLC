package com.Bank_website.fraud.config;

import com.Bank_website.security.SecurityBaseline;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Webhooks are not user endpoints: QStash cannot present a JWT, so the Upstash-Signature check in the
 * controller is the authentication. Everything else (including any user token) is denied.
 */
@Configuration
@EnableWebSecurity
public class ServiceSecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityBaseline baseline) throws Exception {
        baseline.apply(http).authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                .requestMatchers(HttpMethod.POST, "/webhooks/transfer-completed", "/webhooks/customer-created").permitAll()
                .anyRequest().denyAll());
        return http.build();
    }
}
