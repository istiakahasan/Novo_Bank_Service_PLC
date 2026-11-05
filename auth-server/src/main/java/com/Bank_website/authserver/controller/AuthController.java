package com.Bank_website.authserver.controller;

import com.Bank_website.authserver.dto.LoginRequest;
import com.Bank_website.authserver.dto.LoginResponse;
import com.Bank_website.authserver.dto.RefreshRequest;
import com.Bank_website.authserver.dto.RegisterRequest;
import com.Bank_website.authserver.dto.RegisterResponse;
import com.Bank_website.authserver.security.AuthenticatedUser;
import com.Bank_website.authserver.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(
            @Valid @RequestBody RegisterRequest request,
            @RequestHeader(value = "X-Bootstrap-Secret", required = false) String bootstrapSecret,
            Authentication authentication) {
        AuthenticatedUser caller = authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user ? user : null;
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request, caller, bootstrapSecret));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return ResponseEntity.ok(authService.login(request, clientIp(http)));
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(authService.refresh(request));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader(value = "Authorization", required = false) String authorization,
                                       @Valid @RequestBody RefreshRequest request) {
        authService.logout(authorization, request);
        return ResponseEntity.noContent().build();
    }

    /**
     * Render's router appends the real client address to X-Forwarded-For, so the LAST entry is trustworthy;
     * earlier entries can be supplied by the caller.
     */
    static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String[] hops = forwarded.split(",");
            return hops[hops.length - 1].trim();
        }
        return request.getRemoteAddr();
    }
}
