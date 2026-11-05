package com.banking.security.autoconfigure;

import com.banking.security.config.DefaultSecurityConfig;
import com.banking.security.filter.JwtAuthenticationFilter;
import com.banking.security.filter.QStashSignatureFilter;
import com.banking.security.handler.ForbiddenHandler;
import com.banking.security.handler.UnauthorizedHandler;
import com.banking.security.util.JwtValidator;
import com.banking.security.util.QStashSignatureVerifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Registers the shared security beans for every servlet service that has this jar on its classpath.
 * Runs before Boot's own security auto-configuration so DefaultSecurityConfig can replace Boot's default chain.
 */
@AutoConfiguration(beforeName = "org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(SecurityFilterChain.class)
@EnableMethodSecurity
@Import({
        JwtValidator.class,
        JwtAuthenticationFilter.class,
        QStashSignatureVerifier.class,
        QStashSignatureFilter.class,
        UnauthorizedHandler.class,
        ForbiddenHandler.class,
        DefaultSecurityConfig.class
})
public class SecurityAutoConfiguration {

    /**
     * The two filters are Spring beans, and Boot would also register every Filter bean in the servlet container.
     * They must run only inside the Spring Security chain, so the container registration is switched off.
     */
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration(JwtAuthenticationFilter filter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<QStashSignatureFilter> qstashFilterRegistration(QStashSignatureFilter filter) {
        FilterRegistrationBean<QStashSignatureFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    /**
     * Users never log in with a local password in these services. Defining this stops Boot from creating its
     * in-memory "user" with a generated password that is printed to the log at startup.
     */
    @Bean
    @ConditionalOnMissingBean(UserDetailsService.class)
    public UserDetailsService noLocalUsers() {
        return username -> {
            throw new UsernameNotFoundException("This service has no local users");
        };
    }
}
