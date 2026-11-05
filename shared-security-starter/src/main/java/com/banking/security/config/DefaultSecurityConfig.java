package com.banking.security.config;

import com.banking.security.filter.JwtAuthenticationFilter;
import com.banking.security.filter.QStashSignatureFilter;
import com.banking.security.handler.ForbiddenHandler;
import com.banking.security.handler.UnauthorizedHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Applied only when the service defines no SecurityFilterChain of its own. To customise, define a SecurityFilterChain
 * bean (the shared filters are beans: inject them and add them to your chain). Method security stays on either way
 * because it is enabled in SecurityAutoConfiguration.
 */
@Configuration
@EnableWebSecurity
@ConditionalOnMissingBean(SecurityFilterChain.class)
public class DefaultSecurityConfig {

    @Bean
    public SecurityFilterChain bankingSecurityFilterChain(HttpSecurity http,
                                                          JwtAuthenticationFilter jwtFilter,
                                                          QStashSignatureFilter qstashFilter,
                                                          UnauthorizedHandler unauthorizedHandler,
                                                          ForbiddenHandler forbiddenHandler) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e.authenticationEntryPoint(unauthorizedHandler).accessDeniedHandler(forbiddenHandler))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(qstashFilter, JwtAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/api/auth/**", "/api/auth/.well-known/**").permitAll()
                        .requestMatchers("/webhooks/**").permitAll() // authenticated by QStashSignatureFilter
                        .anyRequest().authenticated());
        return http.build();
    }

    /** Origins come from cors.allowed-origins / CORS_ALLOWED_ORIGINS (comma separated). Empty means no cross-origin access. */
    // Spring MVC's HandlerMappingIntrospector is also a CorsConfigurationSource, so the condition is on the bean NAME
    // (the name Spring Security looks up); a service overrides CORS by defining its own bean called corsConfigurationSource.
    @Bean
    @ConditionalOnMissingBean(name = "corsConfigurationSource")
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${cors.allowed-origins:${CORS_ALLOWED_ORIGINS:}}") String allowedOrigins) {
        List<String> origins = Arrays.stream(allowedOrigins.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With", "Idempotency-Key"));
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
