package com.Bank_website.reporting.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "supabase.storage")
public class SupabaseStorageConfig {

    private String bucket = "reports";
    private int signedUrlDurationSeconds = 86400;

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public int getSignedUrlDurationSeconds() {
        return signedUrlDurationSeconds;
    }

    public void setSignedUrlDurationSeconds(int signedUrlDurationSeconds) {
        this.signedUrlDurationSeconds = signedUrlDurationSeconds;
    }
}
