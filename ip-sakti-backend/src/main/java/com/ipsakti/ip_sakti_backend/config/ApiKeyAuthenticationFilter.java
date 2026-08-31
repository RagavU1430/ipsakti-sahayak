package com.ipsakti.ip_sakti_backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-API-Key";

    private final SecurityProperties properties;

    public ApiKeyAuthenticationFilter(SecurityProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!properties.apiKeyRequired() || isHealthCheck(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String configuredApiKey = properties.getApiKey();
        String providedApiKey = request.getHeader(API_KEY_HEADER);
        if (configuredApiKey != null && !configuredApiKey.isBlank() && configuredApiKey.equals(providedApiKey)) {
            var authentication = new UsernamePasswordAuthenticationToken(
                    "api-key-client",
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_API_CLIENT"))
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"Unauthorized\",\"code\":\"UNAUTHORIZED\"}");
    }

    private boolean isHealthCheck(HttpServletRequest request) {
        return "/health".equals(request.getRequestURI()) || "/actuator/health".equals(request.getRequestURI());
    }
}
