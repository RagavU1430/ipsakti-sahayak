package com.ipsakti.ip_sakti_backend.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ipsakti.ip_sakti_backend.config.JwtProperties;
import com.ipsakti.ip_sakti_backend.config.SupabaseProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String DEV_FALLBACK_SECRET = "ip-sakti-dev-jwt-signing-secret-key-32bytes-long!";

    private final JwtProperties jwtProperties;
    private final SupabaseProperties supabaseProperties;
    private final ObjectMapper objectMapper;

    public record JwtClaims(
            String subject,
            String email,
            String role,
            Instant issuedAt,
            Instant expiresAt,
            Map<String, Object> allClaims
    ) {}

    public JwtService(JwtProperties jwtProperties, SupabaseProperties supabaseProperties, org.springframework.beans.factory.ObjectProvider<ObjectMapper> objectMapperProvider) {
        this.jwtProperties = jwtProperties;
        this.supabaseProperties = supabaseProperties;
        this.objectMapper = (objectMapperProvider != null) ? objectMapperProvider.getIfAvailable(ObjectMapper::new) : new ObjectMapper();
    }

    public Optional<JwtClaims> parseAndValidateToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }

        String[] parts = token.trim().split("\\.");
        if (parts.length != 3) {
            log.debug("jwt_invalid_format parts={}", parts.length);
            return Optional.empty();
        }

        String headerB64 = parts[0];
        String payloadB64 = parts[1];
        String signatureB64 = parts[2];

        byte[] secretBytes = resolveSecretBytes();
        if (!verifySignature(headerB64 + "." + payloadB64, signatureB64, secretBytes)) {
            // Also check if signed by anon-key or fallback key if different
            byte[] anonSecretBytes = resolveAnonSecretBytes();
            if (anonSecretBytes == null || !verifySignature(headerB64 + "." + payloadB64, signatureB64, anonSecretBytes)) {
                log.debug("jwt_signature_verification_failed");
                return Optional.empty();
            }
        }

        try {
            byte[] payloadBytes = Base64.getUrlDecoder().decode(payloadB64);
            Map<String, Object> claims = objectMapper.readValue(payloadBytes, new TypeReference<Map<String, Object>>() {});

            // Validate expiration
            if (claims.containsKey("exp")) {
                long exp = ((Number) claims.get("exp")).longValue();
                Instant expiration = Instant.ofEpochSecond(exp);
                if (Instant.now().isAfter(expiration)) {
                    log.debug("jwt_token_expired exp={}", expiration);
                    return Optional.empty();
                }
            }

            // Validate not before
            if (claims.containsKey("nbf")) {
                long nbf = ((Number) claims.get("nbf")).longValue();
                Instant notBefore = Instant.ofEpochSecond(nbf);
                if (Instant.now().isBefore(notBefore)) {
                    log.debug("jwt_token_not_yet_valid nbf={}", notBefore);
                    return Optional.empty();
                }
            }

            String sub = (String) claims.get("sub");
            String email = (String) claims.get("email");
            String role = (String) claims.get("role");
            Instant iat = claims.containsKey("iat") ? Instant.ofEpochSecond(((Number) claims.get("iat")).longValue()) : null;
            Instant exp = claims.containsKey("exp") ? Instant.ofEpochSecond(((Number) claims.get("exp")).longValue()) : null;

            if (sub == null || sub.isBlank()) {
                log.debug("jwt_missing_sub_claim");
                return Optional.empty();
            }

            return Optional.of(new JwtClaims(sub, email, role, iat, exp, claims));
        } catch (Exception e) {
            log.debug("jwt_parsing_failed error={}", e.getMessage());
            return Optional.empty();
        }
    }

    public String generateToken(String subject, String email, Duration ttl) {
        try {
            Map<String, Object> header = Map.of("alg", "HS256", "typ", "JWT");
            Instant now = Instant.now();
            Instant exp = now.plus((ttl != null) ? ttl : jwtProperties.getAccessTokenTtl());

            Map<String, Object> payload = new HashMap<>();
            payload.put("sub", subject);
            if (email != null) {
                payload.put("email", email);
            }
            payload.put("iss", jwtProperties.getIssuer());
            payload.put("iat", now.getEpochSecond());
            payload.put("exp", exp.getEpochSecond());
            payload.put("role", "authenticated");

            String headerB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(objectMapper.writeValueAsBytes(header));
            String payloadB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(objectMapper.writeValueAsBytes(payload));
            String dataToSign = headerB64 + "." + payloadB64;

            byte[] secretBytes = resolveSecretBytes();
            String signatureB64 = sign(dataToSign, secretBytes);

            return dataToSign + "." + signatureB64;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate JWT", e);
        }
    }

    private byte[] resolveSecretBytes() {
        String secret = jwtProperties.getSecret();
        if (secret != null && !secret.isBlank()) {
            return secret.getBytes(StandardCharsets.UTF_8);
        }
        String serviceRoleKey = supabaseProperties.getServiceRoleKey();
        if (serviceRoleKey != null && !serviceRoleKey.isBlank()) {
            return serviceRoleKey.getBytes(StandardCharsets.UTF_8);
        }
        return DEV_FALLBACK_SECRET.getBytes(StandardCharsets.UTF_8);
    }

    private byte[] resolveAnonSecretBytes() {
        String anonKey = supabaseProperties.getAnonKey();
        if (anonKey != null && !anonKey.isBlank()) {
            return anonKey.getBytes(StandardCharsets.UTF_8);
        }
        return null;
    }

    private boolean verifySignature(String data, String signatureB64, byte[] secret) {
        try {
            String expectedSignature = sign(data, secret);
            return MessageDigest.isEqual(
                    signatureB64.getBytes(StandardCharsets.UTF_8),
                    expectedSignature.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            return false;
        }
    }

    private String sign(String data, byte[] secret) throws Exception {
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
        byte[] signatureBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(signatureBytes);
    }
}
