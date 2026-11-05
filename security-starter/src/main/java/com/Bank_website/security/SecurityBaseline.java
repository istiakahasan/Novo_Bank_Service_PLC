package com.Bank_website.security;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Stateless baseline every service applies before adding its own authorizeHttpRequests rules:
 * no sessions/CSRF/basic/form login, JSON 401/403 handlers, JWT and service-key filters.
 */
public class SecurityBaseline {

    private final JwtAuthenticationFilter jwtFilter;
    private final SystemKeyAuthenticationFilter systemFilter;
    private final UnauthorizedHandler unauthorizedHandler;
    private final ForbiddenHandler forbiddenHandler;

    public SecurityBaseline(JwtAuthenticationFilter jwtFilter, SystemKeyAuthenticationFilter systemFilter,
                            UnauthorizedHandler unauthorizedHandler, ForbiddenHandler forbiddenHandler) {
        this.jwtFilter = jwtFilter;
        this.systemFilter = systemFilter;
        this.unauthorizedHandler = unauthorizedHandler;
        this.forbiddenHandler = forbiddenHandler;
    }

    public HttpSecurity apply(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(unauthorizedHandler)
                        .accessDeniedHandler(forbiddenHandler))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(systemFilter, JwtAuthenticationFilter.class);
    }
}
