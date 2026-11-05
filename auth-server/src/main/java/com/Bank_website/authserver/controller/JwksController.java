package com.Bank_website.authserver.controller;

import com.Bank_website.authserver.service.JwtService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class JwksController {

    private final JwtService jwtService;

    public JwksController(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    /** {keys:[{kty:"RSA", kid:"v1", use:"sig", alg:"RS256", n:"...", e:"AQAB"}]} */
    @GetMapping({"/api/auth/.well-known/jwks.json", "/.well-known/jwks.json"})
    public Map<String, Object> jwks() {
        return jwtService.jwks();
    }
}
