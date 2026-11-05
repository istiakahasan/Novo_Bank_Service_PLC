package com.banking.security.testapp;

import com.banking.security.util.RoleExtractor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** A minimal consuming service: NO security code of its own, only the starter on the classpath. */
@SpringBootApplication
public class TestApplication {

    public static final AtomicInteger WEBHOOK_CALLS = new AtomicInteger();

    public static void main(String[] args) {
        SpringApplication.run(TestApplication.class, args);
    }

    @RestController
    static class Endpoints {

        @GetMapping("/actuator/health")
        Map<String, String> health() {
            return Map.of("status", "UP");
        }

        @GetMapping("/api/me")
        Map<String, Object> me(Authentication authentication) {
            return Map.of("role", String.valueOf(RoleExtractor.roleOf(authentication)),
                    "customerId", String.valueOf(RoleExtractor.customerIdOf(authentication)));
        }

        @PreAuthorize("hasRole('STAFF')")
        @GetMapping("/api/staff-only")
        Map<String, String> staffOnly() {
            return Map.of("ok", "staff");
        }

        @PostMapping("/webhooks/echo")
        Map<String, Object> echo(@RequestBody String body) {
            WEBHOOK_CALLS.incrementAndGet();
            return Map.of("received", body);
        }
    }
}
