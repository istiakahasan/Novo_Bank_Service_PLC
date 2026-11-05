package com.banking.security.handler;

import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;

/** Writes {status, message, timestamp}; used by the filters and the two handlers so every 401/403 looks the same. */
public final class JsonErrorWriter {

    private JsonErrorWriter() {
    }

    public static void write(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        if (status == HttpServletResponse.SC_UNAUTHORIZED) {
            response.setHeader("WWW-Authenticate", "Bearer");
        }
        response.getWriter().write("{\"status\":" + status + ",\"message\":\"" + message.replace("\"", "'")
                + "\",\"timestamp\":\"" + OffsetDateTime.now() + "\"}");
    }
}
