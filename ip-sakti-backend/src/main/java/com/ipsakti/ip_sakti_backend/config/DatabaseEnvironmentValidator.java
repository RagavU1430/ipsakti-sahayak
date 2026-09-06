package com.ipsakti.ip_sakti_backend.config;

import jakarta.annotation.PostConstruct;
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Strict production gatekeeper for database configuration.
 * Ensures H2 in-memory mode is strictly restricted to local development and test profiles.
 * In production mode, silent fallbacks to H2 are actively forbidden, ensuring the application
 * fails immediately if Supabase / PostgreSQL is unavailable or unconfigured.
 */
@Component
public class DatabaseEnvironmentValidator {

    private static final Logger log = LoggerFactory.getLogger(DatabaseEnvironmentValidator.class);

    private final Environment environment;
    private final SecurityProperties securityProperties;

    @Value("${spring.datasource.url:}")
    private String datasourceUrl;

    public DatabaseEnvironmentValidator(Environment environment, SecurityProperties securityProperties) {
        this.environment = environment;
        this.securityProperties = securityProperties;
    }

    @PostConstruct
    public void validateDatabaseConfiguration() {
        boolean isProdProfile = Arrays.asList(environment.getActiveProfiles()).contains("prod");
        boolean isProdSecurity = "prod".equalsIgnoreCase(securityProperties.getMode());

        if (isProdProfile || isProdSecurity) {
            if (datasourceUrl == null || datasourceUrl.isBlank() || datasourceUrl.toLowerCase().contains("h2")) {
                throw new IllegalStateException(
                        "CRITICAL PRODUCTION CONFIGURATION ERROR: H2 database is strictly forbidden in PRODUCTION mode. "
                                + "A valid PostgreSQL/Supabase JDBC URL must be configured (spring.datasource.url / SPRING_DATASOURCE_URL). "
                                + "Configured datasource URL: '" + datasourceUrl + "'");
            }
            log.info("Production database configuration validated: PostgreSQL target verified.");
        } else {
            log.info("Non-production environment active (securityMode={}, profiles={}). Datasource target: {}",
                    securityProperties.getMode(),
                    Arrays.toString(environment.getActiveProfiles()),
                    (datasourceUrl != null && datasourceUrl.toLowerCase().contains("h2"))
                            ? "H2 In-Memory (Local/Test Only)"
                            : "External Database");
        }
    }
}
