package com.ipsakti.ip_sakti_backend.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ipsakti.ip_sakti_backend.config.JwtProperties;
import com.ipsakti.ip_sakti_backend.config.SupabaseProperties;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private JwtService jwtService;
    private JwtProperties jwtProperties;
    private SupabaseProperties supabaseProperties;

    @BeforeEach
    void setUp() {
        jwtProperties = new JwtProperties();
        jwtProperties.setSecret("test-secret-key-that-is-at-least-32-bytes-long!");
        jwtProperties.setIssuer("ip-sakti-test");
        jwtProperties.setAccessTokenTtl(Duration.ofMinutes(15));

        supabaseProperties = new SupabaseProperties();
        jwtService = new JwtService(jwtProperties, supabaseProperties, null);
    }

    @Test
    @DisplayName("Successfully generates and validates a valid JWT")
    void testGenerateAndValidateJwt() {
        String token = jwtService.generateToken("user-123", "user@example.com", Duration.ofMinutes(10));
        assertThat(token).isNotBlank();

        Optional<JwtService.JwtClaims> claimsOpt = jwtService.parseAndValidateToken(token);
        assertThat(claimsOpt).isPresent();

        JwtService.JwtClaims claims = claimsOpt.get();
        assertThat(claims.subject()).isEqualTo("user-123");
        assertThat(claims.email()).isEqualTo("user@example.com");
        assertThat(claims.role()).isEqualTo("authenticated");
    }

    @Test
    @DisplayName("Rejects expired JWT token")
    void testRejectExpiredToken() {
        String expiredToken = jwtService.generateToken("user-123", "user@example.com", Duration.ofSeconds(-10));
        Optional<JwtService.JwtClaims> claimsOpt = jwtService.parseAndValidateToken(expiredToken);
        assertThat(claimsOpt).isEmpty();
    }

    @Test
    @DisplayName("Rejects tampered JWT signature")
    void testRejectTamperedToken() {
        String token = jwtService.generateToken("user-123", "user@example.com", Duration.ofMinutes(10));
        String[] parts = token.split("\\.");
        String tamperedToken = parts[0] + "." + parts[1] + ".invalidSignatureB64";

        Optional<JwtService.JwtClaims> claimsOpt = jwtService.parseAndValidateToken(tamperedToken);
        assertThat(claimsOpt).isEmpty();
    }

    @Test
    @DisplayName("Rejects null or malformed tokens")
    void testRejectMalformedTokens() {
        assertThat(jwtService.parseAndValidateToken(null)).isEmpty();
        assertThat(jwtService.parseAndValidateToken("")).isEmpty();
        assertThat(jwtService.parseAndValidateToken("invalid.token")).isEmpty();
        assertThat(jwtService.parseAndValidateToken("not-a-jwt")).isEmpty();
    }
}
