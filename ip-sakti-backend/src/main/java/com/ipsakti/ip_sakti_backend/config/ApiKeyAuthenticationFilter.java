package com.ipsakti.ip_sakti_backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
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
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())
                || !properties.apiKeyRequired()
                || isPublicOrStatic(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String configuredApiKey = properties.getApiKey();
        String providedApiKey = request.getHeader(API_KEY_HEADER);
        if (configuredApiKey != null && !configuredApiKey.isBlank() && providedApiKey != null
                && MessageDigest.isEqual(
                        configuredApiKey.getBytes(StandardCharsets.UTF_8),
                        providedApiKey.getBytes(StandardCharsets.UTF_8))) {
            var existingAuth = SecurityContextHolder.getContext().getAuthentication();
            if (existingAuth == null || !existingAuth.isAuthenticated() || "anonymousUser".equals(existingAuth.getName())) {
                var authentication = new UsernamePasswordAuthenticationToken(
                        "api-key-client",
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_API_CLIENT"))
                );
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"Unauthorized\",\"code\":\"UNAUTHORIZED\"}");
    }

    private boolean isPublicOrStatic(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri.startsWith("/health") || uri.startsWith("/actuator/health")) {
            return true;
        }
        return uri.equals("/") || uri.equals("/index.html") || uri.startsWith("/assets/")
                || uri.endsWith(".js") || uri.endsWith(".css") || uri.endsWith(".svg")
                || uri.endsWith(".png") || uri.endsWith(".ico") || uri.equals("/manifest.json")
                || uri.equals("/ask") || uri.equals("/tk") || uri.equals("/formulations")
                || uri.equals("/formulation") || uri.equals("/regulatory")
                || uri.startsWith("/history") || uri.equals("/login") || uri.equals("/account")
                || uri.equals("/about");
    }
}
