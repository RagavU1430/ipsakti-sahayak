package com.ipsakti.ip_sakti_backend.config;

import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableConfigurationProperties({
        GeminiProperties.class,
        JwtProperties.class,
        RagProperties.class,
        SupabaseProperties.class,
        SecurityProperties.class
})
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            SecurityProperties securityProperties,
            org.springframework.beans.factory.ObjectProvider<com.ipsakti.ip_sakti_backend.auth.JwtAuthenticationFilter> jwtAuthenticationFilterProvider
    ) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> {})
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .exceptionHandling(ex -> ex.authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType(org.springframework.http.MediaType.APPLICATION_JSON_VALUE);
                    response.getWriter().write("{\"error\":\"Unauthorized\",\"code\":\"UNAUTHORIZED\",\"message\":\"Authentication is required to access this resource.\"}");
                }));

        com.ipsakti.ip_sakti_backend.auth.JwtAuthenticationFilter jwtFilter = jwtAuthenticationFilterProvider.getIfAvailable();
        if (jwtFilter != null) {
            http.addFilterBefore(jwtFilter, AnonymousAuthenticationFilter.class);
        }

        if (securityProperties.apiKeyRequired()) {
            http.addFilterBefore(new ApiKeyAuthenticationFilter(securityProperties), AnonymousAuthenticationFilter.class)
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                            .requestMatchers("/health", "/health/**", "/actuator/health").permitAll()
                            .requestMatchers(
                                    "/",
                                    "/index.html",
                                    "/assets/**",
                                    "/favicon.ico",
                                    "/favicon.svg",
                                    "/*.js",
                                    "/*.css",
                                    "/*.svg",
                                    "/*.png",
                                    "/*.ico",
                                    "/manifest.json",
                                    "/ask",
                                    "/tk",
                                    "/formulations",
                                    "/regulatory",
                                    "/history",
                                    "/history/**",
                                    "/login",
                                    "/account",
                                    "/about"
                            ).permitAll()
                            .anyRequest().authenticated());
        } else {
            http.authorizeHttpRequests(auth -> auth
                    .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                    .requestMatchers(
                            "/health",
                            "/health/**",
                            "/actuator/health",
                            "/",
                            "/index.html",
                            "/assets/**",
                            "/favicon.ico",
                            "/favicon.svg",
                            "/*.js",
                            "/*.css",
                            "/*.svg",
                            "/*.png",
                            "/*.ico",
                            "/manifest.json",
                            "/ask",
                            "/tk",
                            "/formulations",
                            "/regulatory",
                            "/history",
                            "/history/**",
                            "/login",
                            "/account",
                            "/about",
                            "/api/v1/ask",
                            "/api/v1/questions",
                            "/api/v1/questions/health",
                            "/api/v1/tk/overlap",
                            "/api/v1/formulations/classify",
                            "/api/v1/regulatory/analyze"
                    ).permitAll()
                    .requestMatchers("/api/v1/conversations/**").authenticated()
                    .anyRequest().denyAll());
        }

        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(SecurityProperties securityProperties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(securityProperties.getAllowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Content-Type", "Authorization", "X-API-Key"));
        configuration.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    UserDetailsService userDetailsService() {
        return username -> {
            throw new UsernameNotFoundException("No password-based users are configured.");
        };
    }

    @Bean
    com.ipsakti.ip_sakti_backend.auth.JwtAuthenticationFilter jwtAuthenticationFilter(
            org.springframework.beans.factory.ObjectProvider<com.ipsakti.ip_sakti_backend.auth.JwtService> jwtServiceProvider,
            org.springframework.beans.factory.ObjectProvider<com.ipsakti.ip_sakti_backend.auth.UserService> userServiceProvider,
            SecurityProperties securityProperties
    ) {
        return new com.ipsakti.ip_sakti_backend.auth.JwtAuthenticationFilter(
                jwtServiceProvider.getIfAvailable(),
                userServiceProvider.getIfAvailable(),
                securityProperties
        );
    }
}
