package com.preonsurl.apis.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class ApiKeyAuthInterceptor implements HandlerInterceptor {

    private final String configuredApiKey;

    public ApiKeyAuthInterceptor(@Value("${preonsurl.api.key:}") String configuredApiKey) {
        this.configuredApiKey = configuredApiKey;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String apiKey = request.getHeader("X-API-KEY");
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = request.getHeader("x-api-key");
        }
        if (apiKey == null || apiKey.isBlank()) {
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                apiKey = authHeader.substring(7).trim();
            }
        }

        if (apiKey != null && !apiKey.isBlank() && configuredApiKey != null && configuredApiKey.equals(apiKey)) {
            return true;
        }

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"Invalid or missing API key\"}");
        return false;
    }
}
