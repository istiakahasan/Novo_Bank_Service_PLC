package com.Bank_website.security;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/**
 * Adds X-Service-Id / X-Service-Key to outbound calls to another Novo service.
 * Attach it ONLY to the RestClient that talks to the Customer Service (never to the QStash or other
 * third-party clients, or the secret would leak).
 */
public class SystemAuthInterceptor implements ClientHttpRequestInterceptor {

    private final String serviceId;
    private final String serviceKey;

    public SystemAuthInterceptor(String serviceId, String serviceKey) {
        this.serviceId = serviceId;
        this.serviceKey = serviceKey;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        if (serviceId != null && !serviceId.isBlank() && serviceKey != null && !serviceKey.isBlank()) {
            request.getHeaders().set(SystemKeyAuthenticationFilter.ID_HEADER, serviceId);
            request.getHeaders().set(SystemKeyAuthenticationFilter.KEY_HEADER, serviceKey);
        }
        return execution.execute(request, body);
    }
}
