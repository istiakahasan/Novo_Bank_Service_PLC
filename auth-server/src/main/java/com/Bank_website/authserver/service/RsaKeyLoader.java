package com.Bank_website.authserver.service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Reads RSA keys from an environment variable in any of the usual shapes:
 * PEM text, base64 of the PEM file ("base64 -w0 jwt_private.pem"), or base64 of the DER bytes;
 * private keys may be PKCS#8 ("BEGIN PRIVATE KEY") or PKCS#1 ("BEGIN RSA PRIVATE KEY", what `openssl genrsa` wrote before 3.0).
 */
final class RsaKeyLoader {

    private static final byte[] RSA_ALGORITHM_ID = {
            0x30, 0x0d, 0x06, 0x09, 0x2a, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xf7, 0x0d, 0x01, 0x01, 0x01, 0x05, 0x00};

    private RsaKeyLoader() {
    }

    static RSAPrivateKey privateKey(String raw) {
        try {
            byte[] der = toDer(raw, true);
            return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (GeneralSecurityException | RuntimeException e) {
            throw new IllegalStateException("jwt.private-key is not a valid RSA private key", e);
        }
    }

    static RSAPublicKey publicKey(String raw) {
        try {
            byte[] der = toDer(raw, false);
            return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
        } catch (GeneralSecurityException | RuntimeException e) {
            throw new IllegalStateException("jwt.public-key is not a valid RSA public key", e);
        }
    }

    /** The public half of an RSA private key (requires the CRT form, which every PKCS#8/PKCS#1 RSA key has). */
    static RSAPublicKey derivePublic(RSAPrivateKey privateKey) {
        try {
            RSAPrivateCrtKey crt = (RSAPrivateCrtKey) privateKey;
            return (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new RSAPublicKeySpec(crt.getModulus(), crt.getPublicExponent()));
        } catch (GeneralSecurityException | ClassCastException e) {
            throw new IllegalStateException("Cannot derive the public key from jwt.private-key", e);
        }
    }

    private static byte[] toDer(String raw, boolean isPrivate) {
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("empty key");
        }
        if (!text.contains("-----BEGIN")) {
            byte[] decoded = Base64.getMimeDecoder().decode(text);
            String asText = new String(decoded, StandardCharsets.US_ASCII);
            if (!asText.contains("-----BEGIN")) {
                return decoded; // base64 of the DER bytes
            }
            text = asText;     // base64 of the PEM file
        }
        boolean pkcs1 = text.contains("BEGIN RSA PRIVATE KEY");
        byte[] der = Base64.getMimeDecoder().decode(text.replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", ""));
        return isPrivate && pkcs1 ? pkcs1ToPkcs8(der) : der;
    }

    /** PrivateKeyInfo ::= SEQUENCE { version 0, AlgorithmIdentifier rsaEncryption, OCTET STRING (the PKCS#1 key) } */
    static byte[] pkcs1ToPkcs8(byte[] pkcs1) {
        byte[] version = {0x02, 0x01, 0x00};
        byte[] octetString = der(0x04, pkcs1);
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.writeBytes(version);
        body.writeBytes(RSA_ALGORITHM_ID);
        body.writeBytes(octetString);
        return der(0x30, body.toByteArray());
    }

    private static byte[] der(int tag, byte[] content) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(tag);
        int length = content.length;
        if (length < 128) {
            out.write(length);
        } else {
            int bytes = length > 0xFFFF ? 3 : length > 0xFF ? 2 : 1;
            out.write(0x80 | bytes);
            for (int i = bytes - 1; i >= 0; i--) {
                out.write((length >> (8 * i)) & 0xFF);
            }
        }
        out.writeBytes(content);
        return out.toByteArray();
    }
}
