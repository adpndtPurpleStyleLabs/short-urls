package com.preonsurl.apis.auth.security;

import com.preonsurl.apis.apikey.ApiKeyService;
import com.preonsurl.apis.auth.dto.AuthenticatedUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;


@Component
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {
    private static final String API_KEY_HEADER = "X-API-Key";

    private final ApiKeyService apiKeyService;

    public ApiKeyAuthenticationFilter(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String apiKey = request.getHeader(API_KEY_HEADER);
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = request.getHeader("X-API-KEY");
        }
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = request.getHeader("x-api-key");
        }

        if (apiKey == null || apiKey.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            AuthenticatedUser authenticatedUser = apiKeyService.authenticate(apiKey);

            if (authenticatedUser != null) {
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(authenticatedUser, null, List.of());
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } else {
                request.setAttribute("apiKeyInvalid", Boolean.TRUE);
                SecurityContextHolder.clearContext();
            }

        } catch (Exception e) {
            request.setAttribute("apiKeyInvalid", Boolean.TRUE);
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}
