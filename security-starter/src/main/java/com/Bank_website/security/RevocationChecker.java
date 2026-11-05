package com.Bank_website.security;

/** Logout / revocation list lookup. Redis key: revoked:jti:{jti}, TTL = remaining token lifetime. */
public interface RevocationChecker {

    String KEY_PREFIX = "revoked:jti:";

    boolean isRevoked(String jti);
}
