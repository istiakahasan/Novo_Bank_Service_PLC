package com.Bank_website.security;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public final class KeyParsing {

    private KeyParsing() {
    }

    /** Accepts base64 DER (X.509 SubjectPublicKeyInfo) or a PEM block. */
    public static PublicKey rsaPublicKey(String encoded) {
        try {
            String base64 = encoded.replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", "");
            byte[] der = Base64.getDecoder().decode(base64);
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("security.jwt.public-key is not a valid RSA public key", e);
        }
    }
}
