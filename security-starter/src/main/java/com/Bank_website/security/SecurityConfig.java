package com.Bank_website.security;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;

/** Auto-configuration: wires verifier, filters, handlers and the outbound interceptor. */
@AutoConfiguration(afterName = "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration")
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfig {

    @Bean
    public RoleExtractor roleExtractor() {
        return new RoleExtractor();
    }

    @Bean
    public JwtVerifier jwtVerifier(SecurityProperties props, RoleExtractor roleExtractor) {
        List<PublicKey> keys = new ArrayList<>();
        if (!props.getJwt().getPublicKey().isBlank()) {
            keys.add(KeyParsing.rsaPublicKey(props.getJwt().getPublicKey()));
        }
        if (!props.getJwt().getPublicKeyNext().isBlank()) {
            keys.add(KeyParsing.rsaPublicKey(props.getJwt().getPublicKeyNext()));
        }
        return new JwtVerifier(keys, props.getJwt().getIssuer(), roleExtractor);
    }

    @Bean
    public UnauthorizedHandler unauthorizedHandler() {
        return new UnauthorizedHandler();
    }

    @Bean
    public ForbiddenHandler forbiddenHandler() {
        return new ForbiddenHandler();
    }

    @Bean
    @ConditionalOnMissingBean(RevocationChecker.class)
    public RevocationChecker revocationChecker(org.springframework.beans.factory.ObjectProvider<StringRedisTemplate> redis,
                                               SecurityProperties props) {
        StringRedisTemplate template = redis.getIfAvailable();
        if (template == null) {
            return jti -> false; // no Redis in this service: revocation list unavailable
        }
        return new RedisRevocationChecker(template, props.getJwt().isRevocationFailOpen());
    }

    @Bean
    public SecurityBaseline securityBaseline(JwtVerifier verifier, RevocationChecker revocationChecker,
                                             RoleExtractor roleExtractor, UnauthorizedHandler unauthorized,
                                             ForbiddenHandler forbidden, SecurityProperties props) {
        return new SecurityBaseline(
                new JwtAuthenticationFilter(verifier, revocationChecker, roleExtractor, unauthorized),
                new SystemKeyAuthenticationFilter(props.getSystem().getKeys(), unauthorized),
                unauthorized, forbidden);
    }

    /** Attach to the RestClient that calls the Customer Service only. */
    @Bean
    public SystemAuthInterceptor systemAuthInterceptor(SecurityProperties props) {
        return new SystemAuthInterceptor(props.getSystem().getServiceId(), props.getSystem().getServiceKey());
    }
}
