package com.doFast.dofastapp.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.io.IOException;

final class RateLimitHttpResponseWriter {

    private RateLimitHttpResponseWriter() {}

    static void writeTooManyRequests(HttpServletResponse response, long retryAfterSeconds) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", Long.toString(retryAfterSeconds));
        writeJson(response, "{\"status\":429,\"error\":\"Too Many Requests\"}");
    }

    static void writeBackendUnavailable(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        response.setHeader("Retry-After", "1");
        writeJson(response, "{\"status\":503,\"error\":\"Service Unavailable\"}");
    }

    private static void writeJson(HttpServletResponse response, String body) throws IOException {
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(body);
    }
}
