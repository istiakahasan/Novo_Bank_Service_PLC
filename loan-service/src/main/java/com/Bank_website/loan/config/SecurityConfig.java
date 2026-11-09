package com.Bank_website.loan.config;

import com.Bank_website.security.SecurityBaseline;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityBaseline baseline) throws Exception {
        baseline.apply(http).authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                .requestMatchers("/webhooks/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/loans/products").permitAll()
                .requestMatchers("/api/loans/**").hasAnyRole("CUSTOMER", "STAFF")
                .anyRequest().denyAll());
        return http.build();
    }
}
