package com.banking.security;

import com.banking.security.testapp.TestApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A service that defines its own SecurityFilterChain replaces the default one; method security stays on. */
@SpringBootTest(classes = TestApplication.class, properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"})
@AutoConfigureMockMvc
@Import(OverrideTest.OwnChain.class)
class OverrideTest {

    @TestConfiguration
    static class OwnChain {
        @Bean
        SecurityFilterChain own(HttpSecurity http) throws Exception {
            http.csrf(c -> c.disable()).authorizeHttpRequests(a -> a.anyRequest().permitAll());
            return http.build();
        }
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("jwt.public-key", () -> TestKeys.publicKeyBase64Der(TestKeys.PAIR));
    }

    @Autowired ApplicationContext context;
    @Autowired MockMvc mvc;

    @Test
    void theServicesOwnChainWinsAndNoDefaultChainIsAdded() throws Exception {
        assertEquals(1, context.getBeansOfType(SecurityFilterChain.class).size());
        mvc.perform(get("/api/me")).andExpect(status().isOk()); // permitAll in the service's own chain
    }

    @Test
    void methodSecurityIsStillEnforcedWithACustomChain() throws Exception {
        // anonymous caller on a @PreAuthorize endpoint is refused even though the chain permits everything
        mvc.perform(get("/api/staff-only")).andExpect(status().is4xxClientError());
    }
}
