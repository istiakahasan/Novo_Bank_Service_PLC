package com.Bank_website.authserver.controller;

import com.Bank_website.authserver.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth/admin")
public class AdminController {

    private final AuthService authService;

    public AdminController(AuthService authService) {
        this.authService = authService;
    }

    @PreAuthorize("hasRole('STAFF')")
    @PostMapping("/unlock/{userId}")
    public ResponseEntity<Map<String, Object>> unlock(@PathVariable UUID userId) {
        authService.unlock(userId);
        return ResponseEntity.ok(Map.of("userId", userId, "unlocked", true));
    }
}
