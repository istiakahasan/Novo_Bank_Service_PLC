package com.Bank_website.auth.config;

import com.Bank_website.auth.dto.AuthDtos.CreateUserRequest;
import com.Bank_website.auth.repository.AuthUserRepository;
import com.Bank_website.auth.service.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** First-run seeding so you can log in without touching SQL. Does nothing once any user exists. */
@Component
public class BootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapRunner.class);
    private static final UUID ALICE = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID BOB = UUID.fromString("22222222-2222-4222-8222-222222222222");

    private final AuthUserRepository users;
    private final AuthService authService;
    private final String password;
    private final boolean demo;

    public BootstrapRunner(AuthUserRepository users, AuthService authService,
                           @Value("${auth.bootstrap.password:}") String password,
                           @Value("${auth.bootstrap.demo:false}") boolean demo) {
        this.users = users;
        this.authService = authService;
        this.password = password;
        this.demo = demo;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (password.isBlank() || users.count() > 0) {
            return;
        }
        authService.createUser(new CreateUserRequest("staff", password, "STAFF", null));
        log.warn("Bootstrap: created STAFF user 'staff'. Change the password / remove AUTH_BOOTSTRAP_PASSWORD now.");
        if (demo) {
            authService.createUser(new CreateUserRequest("alice", password, "CUSTOMER", ALICE));
            authService.createUser(new CreateUserRequest("bob", password, "CUSTOMER", BOB));
            log.warn("Bootstrap: created demo CUSTOMER users 'alice' and 'bob' (demo only).");
        }
    }
}
