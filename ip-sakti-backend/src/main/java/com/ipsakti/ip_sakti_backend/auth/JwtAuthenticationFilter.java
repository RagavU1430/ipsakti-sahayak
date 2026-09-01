package com.ipsakti.ip_sakti_backend.auth;

import com.ipsakti.ip_sakti_backend.config.SecurityProperties;
import com.ipsakti.ip_sakti_backend.conversation.entity.UserEntity;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String DEV_USER_ID_HEADER = "X-Dev-User-Id";
    private static final String USER_ID_HEADER = "X-User-Id";

    private final JwtService jwtService;
    private final UserService userService;
    private final SecurityProperties securityProperties;

    public JwtAuthenticationFilter(JwtService jwtService, UserService userService, SecurityProperties securityProperties) {
        this.jwtService = jwtService;
        this.userService = userService;
        this.securityProperties = securityProperties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if (jwtService == null || userService == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader(AUTHORIZATION_HEADER);
        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            String token = authHeader.substring(BEARER_PREFIX.length()).trim();
            Optional<JwtService.JwtClaims> claimsOpt = jwtService.parseAndValidateToken(token);
            if (claimsOpt.isPresent()) {
                JwtService.JwtClaims claims = claimsOpt.get();
                UserEntity user = userService.getOrCreateUser(
                        claims.subject(),
                        claims.email(),
                        claims.email() != null ? claims.email().split("@")[0] : null
                );
                UserPrincipal principal = UserPrincipal.of(user.getId(), user.getExternalAuthId(), user.getEmail());
                SecurityContextHolder.getContext().setAuthentication(principal);
                log.debug("jwt_auth_success externalAuthId={} userId={}", user.getExternalAuthId(), user.getId());
            } else {
                log.debug("jwt_auth_invalid_token");
            }
        } else if (securityProperties.isDevMode()) {
            // Support explicit dev user header for multi-user test isolation in dev mode
            String devUserId = request.getHeader(DEV_USER_ID_HEADER);
            if (devUserId == null || devUserId.isBlank()) {
                devUserId = request.getHeader(USER_ID_HEADER);
            }

            if (devUserId != null && !devUserId.isBlank()) {
                UserEntity user = userService.getOrCreateUser(
                        devUserId.trim(),
                        devUserId.trim() + "@dev.local",
                        "Dev User " + devUserId.trim()
                );
                UserPrincipal principal = UserPrincipal.of(user.getId(), user.getExternalAuthId(), user.getEmail());
                SecurityContextHolder.getContext().setAuthentication(principal);
                log.debug("dev_header_auth_success externalAuthId={} userId={}", user.getExternalAuthId(), user.getId());
            }
        }

        filterChain.doFilter(request, response);
    }
}
