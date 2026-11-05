package com.Bank_website.support.config;

import com.Bank_website.security.SecurityBaseline;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * /webhooks/** has no JWT (QStash cannot send one): the Upstash-Signature check in the controller is its
 * authentication. /api/** needs a valid JWT; the role per endpoint is enforced with @PreAuthorize.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityBaseline baseline) throws Exception {
        baseline.apply(http).authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                .requestMatchers("/webhooks/**").permitAll()
                .requestMatchers("/api/**").hasAnyRole("CUSTOMER", "STAFF")
                .anyRequest().denyAll());
        return http.build();
    }
}
