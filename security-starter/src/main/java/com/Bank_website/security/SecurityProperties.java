package com.Bank_website.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * security.jwt.public-key       base64 X.509 (SubjectPublicKeyInfo) DER or PEM of the auth server's RSA public key
 * security.jwt.public-key-next  optional second key, accepted during key rotation
 * security.system.keys          inbound service keys: "ledger=k1,k2;fraud-service=k3"  (several keys per id = rotation)
 * security.system.service-id    outbound identity of THIS service (sent as X-Service-Id)
 * security.system.service-key   outbound secret of THIS service (sent as X-Service-Key)
 */
@ConfigurationProperties(prefix = "security")
public class SecurityProperties {

    private final Jwt jwt = new Jwt();
    private final System system = new System();

    public Jwt getJwt() { return jwt; }
    public System getSystem() { return system; }

    public static class Jwt {
        private String publicKey = "";
        private String publicKeyNext = "";
        private String issuer = "novo-auth";
        /** false = if Redis cannot be reached the token is rejected (secure default). */
        private boolean revocationFailOpen = false;

        public String getPublicKey() { return publicKey; }
        public void setPublicKey(String publicKey) { this.publicKey = publicKey; }
        public String getPublicKeyNext() { return publicKeyNext; }
        public void setPublicKeyNext(String publicKeyNext) { this.publicKeyNext = publicKeyNext; }
        public String getIssuer() { return issuer; }
        public void setIssuer(String issuer) { this.issuer = issuer; }
        public boolean isRevocationFailOpen() { return revocationFailOpen; }
        public void setRevocationFailOpen(boolean revocationFailOpen) { this.revocationFailOpen = revocationFailOpen; }
    }

    public static class System {
        private String keys = "";
        private String serviceId = "";
        private String serviceKey = "";

        public String getKeys() { return keys; }
        public void setKeys(String keys) { this.keys = keys; }
        public String getServiceId() { return serviceId; }
        public void setServiceId(String serviceId) { this.serviceId = serviceId; }
        public String getServiceKey() { return serviceKey; }
        public void setServiceKey(String serviceKey) { this.serviceKey = serviceKey; }
    }
}
