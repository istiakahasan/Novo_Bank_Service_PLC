package com.Bank_website.auth.controller;

import com.Bank_website.auth.dto.AuthDtos.CreateUserRequest;
import com.Bank_website.auth.dto.AuthDtos.LoginRequest;
import com.Bank_website.auth.dto.AuthDtos.LogoutRequest;
import com.Bank_website.auth.dto.AuthDtos.RefreshRequest;
import com.Bank_website.auth.dto.AuthDtos.TokenResponse;
import com.Bank_website.auth.dto.AuthDtos.UserResponse;
import com.Bank_website.auth.service.AuthException;
import com.Bank_website.auth.service.AuthService;
import com.Bank_website.auth.service.TokenService;
import io.jsonwebtoken.Claims;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final TokenService tokenService;

    public AuthController(AuthService authService, TokenService tokenService) {
        this.authService = authService;
        this.tokenService = tokenService;
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request.username(), request.password()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(authService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader(value = "Authorization", required = false) String authorization,
                                       @Valid @RequestBody LogoutRequest request) {
        Claims claims = authService.authenticate(authorization);
        authService.logout(claims, request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/users")
    public ResponseEntity<UserResponse> createUser(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody CreateUserRequest request) {
        Claims claims = authService.authenticate(authorization);
        Object roles = claims.get("roles");
        if (!(roles instanceof List<?> list) || !list.contains("STAFF")) {
            throw new AuthException(HttpStatus.FORBIDDEN, "Only STAFF can create users");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.createUser(request));
    }

    /** Public key other services verify with (also available as the JWT_PUBLIC_KEY env var). */
    @GetMapping("/public-key")
    public Map<String, String> publicKey() {
        return Map.of("algorithm", "RS256", "publicKey", tokenService.publicKeyBase64());
    }
}
