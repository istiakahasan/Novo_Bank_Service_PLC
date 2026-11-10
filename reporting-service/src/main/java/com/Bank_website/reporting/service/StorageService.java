package com.Bank_website.reporting.service;

import com.Bank_website.reporting.config.SupabaseStorageConfig;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;

@Component
public class StorageService {

    private static final Logger log = LoggerFactory.getLogger(StorageService.class);

    private final RestClient restClient;
    private final String supabaseUrl;
    private final String serviceKey;
    private final SupabaseStorageConfig storageConfig;

    public StorageService(@Value("${supabase.url:}") String supabaseUrl,
                          @Value("${supabase.service-key:}") String serviceKey,
                          SupabaseStorageConfig storageConfig) {
        this.supabaseUrl = supabaseUrl != null ? supabaseUrl.replaceAll("/+$", "") : "";
        this.serviceKey = serviceKey != null ? serviceKey : "";
        this.storageConfig = storageConfig;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(10).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(30).toMillis());

        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .build();
    }

    /**
     * Uploads bytes to private Supabase Storage bucket and returns a signed URL valid for 24 hours.
     *
     * @param path        Destination path within bucket (e.g., "statements/uuid.pdf")
     * @param bytes       File payload
     * @param contentType MIME type (application/pdf or text/csv)
     * @return 24-hour signed download URL
     */
    public String uploadAndSign(String path, byte[] bytes, String contentType) {
        String bucket = storageConfig.getBucket();
        int expiresInSeconds = storageConfig.getSignedUrlDurationSeconds();

        if (supabaseUrl.isBlank() || serviceKey.isBlank()) {
            log.warn("Supabase URL or Service Key not configured; generating local mock signed URL for path {}", path);
            return "https://mock-supabase.local/storage/v1/object/sign/" + bucket + "/" + path + "?token=mock-token-24h";
        }

        // 1. Upload object to Supabase Storage bucket
        String uploadUri = supabaseUrl + "/storage/v1/object/" + bucket + "/" + path;
        try {
            restClient.post()
                    .uri(uploadUri)
                    .header("Authorization", "Bearer " + serviceKey)
                    .header("apikey", serviceKey)
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(bytes)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Uploaded {} bytes to Supabase storage bucket {} at path {}", bytes.length, bucket, path);
        } catch (Exception ex) {
            log.error("Failed to upload object to Supabase storage at {}: {}", uploadUri, ex.getMessage(), ex);
            throw new IllegalStateException("Failed to upload file to Supabase storage: " + ex.getMessage(), ex);
        }

        // 2. Request 24h signed URL
        String signUri = supabaseUrl + "/storage/v1/object/sign/" + bucket + "/" + path;
        try {
            JsonNode response = restClient.post()
                    .uri(signUri)
                    .header("Authorization", "Bearer " + serviceKey)
                    .header("apikey", serviceKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("expiresIn", expiresInSeconds))
                    .retrieve()
                    .body(JsonNode.class);

            if (response == null || !response.hasNonNull("signedURL")) {
                throw new IllegalStateException("Supabase sign endpoint returned null or missing signedURL");
            }

            String signedPath = response.get("signedURL").asText();
            if (signedPath.startsWith("http://") || signedPath.startsWith("https://")) {
                return signedPath;
            }
            return supabaseUrl + (signedPath.startsWith("/") ? "" : "/") + signedPath;
        } catch (Exception ex) {
            log.error("Failed to generate signed URL for path {}: {}", path, ex.getMessage(), ex);
            throw new IllegalStateException("Failed to sign file URL: " + ex.getMessage(), ex);
        }
    }
}
