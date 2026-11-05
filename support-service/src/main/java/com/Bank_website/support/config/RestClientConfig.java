package com.Bank_website.support.config;

import com.Bank_website.security.SystemAuthInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class RestClientConfig {

    /**
     * Client for the Customer API: 5 s timeouts, and the SYSTEM credentials (X-Service-Id / X-Service-Key) on every call.
     * The interceptor is attached to this client only, never to the QStash client.
     */
    @Bean("customerServiceClient")
    public RestClient customerServiceClient(@Value("${customer.service.url}") String baseUrl,
                                            SystemAuthInterceptor systemAuth) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(5).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(5).toMillis());
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).requestInterceptor(systemAuth).build();
    }
}
