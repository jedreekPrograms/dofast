package com.doFast.dofastapp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
public class WebSocketOriginPolicy {

    private final String[] allowedOriginPatterns;

    public WebSocketOriginPolicy(
            @Value("${dofast.security.websocket.allowed-origin-patterns:http://localhost:*,http://127.0.0.1:*}") String configuredPatterns
    ) {
        this.allowedOriginPatterns = parse(configuredPatterns);
    }

    public String[] allowedOriginPatterns() {
        return allowedOriginPatterns.clone();
    }

    static String[] parse(String configuredPatterns) {
        if (configuredPatterns == null || configuredPatterns.isBlank()) {
            throw new IllegalArgumentException("At least one WebSocket allowed origin pattern must be configured");
        }

        String[] patterns = Arrays.stream(configuredPatterns.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .toArray(String[]::new);

        if (patterns.length == 0) {
            throw new IllegalArgumentException("At least one WebSocket allowed origin pattern must be configured");
        }
        if (Arrays.asList(patterns).contains("*")) {
            throw new IllegalArgumentException("Wildcard WebSocket origin '*' is not allowed; configure explicit trusted origins");
        }
        return patterns;
    }
}
