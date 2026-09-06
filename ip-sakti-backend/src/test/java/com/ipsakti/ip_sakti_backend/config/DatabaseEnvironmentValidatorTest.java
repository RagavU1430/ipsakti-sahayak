package com.ipsakti.ip_sakti_backend.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DatabaseEnvironmentValidatorTest {

    @Test
    @DisplayName("Dev mode with H2 database URL is permitted for local/test development")
    void permitsH2InDevMode() {
        Environment env = mock(Environment.class);
        when(env.getActiveProfiles()).thenReturn(new String[]{"dev"});

        SecurityProperties securityProps = new SecurityProperties();
        securityProps.setMode("dev");

        DatabaseEnvironmentValidator validator = new DatabaseEnvironmentValidator(env, securityProps);
        ReflectionTestUtils.setField(validator, "datasourceUrl", "jdbc:h2:mem:ipsaktidb;MODE=PostgreSQL");

        assertThatCode(validator::validateDatabaseConfiguration).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Prod security mode with H2 database URL throws IllegalStateException (no silent H2 fallback)")
    void rejectsH2InProdSecurityMode() {
        Environment env = mock(Environment.class);
        when(env.getActiveProfiles()).thenReturn(new String[]{});

        SecurityProperties securityProps = new SecurityProperties();
        securityProps.setMode("prod");

        DatabaseEnvironmentValidator validator = new DatabaseEnvironmentValidator(env, securityProps);
        ReflectionTestUtils.setField(validator, "datasourceUrl", "jdbc:h2:mem:ipsaktidb;MODE=PostgreSQL");

        assertThatThrownBy(validator::validateDatabaseConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CRITICAL PRODUCTION CONFIGURATION ERROR: H2 database is strictly forbidden in PRODUCTION mode");
    }

    @Test
    @DisplayName("Prod profile with H2 database URL throws IllegalStateException")
    void rejectsH2InProdProfile() {
        Environment env = mock(Environment.class);
        when(env.getActiveProfiles()).thenReturn(new String[]{"prod"});

        SecurityProperties securityProps = new SecurityProperties();
        securityProps.setMode("dev");

        DatabaseEnvironmentValidator validator = new DatabaseEnvironmentValidator(env, securityProps);
        ReflectionTestUtils.setField(validator, "datasourceUrl", "jdbc:h2:mem:ipsaktidb");

        assertThatThrownBy(validator::validateDatabaseConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("H2 database is strictly forbidden in PRODUCTION mode");
    }

    @Test
    @DisplayName("Prod mode with PostgreSQL / Supabase URL is permitted")
    void permitsPostgreSqlInProdMode() {
        Environment env = mock(Environment.class);
        when(env.getActiveProfiles()).thenReturn(new String[]{"prod"});

        SecurityProperties securityProps = new SecurityProperties();
        securityProps.setMode("prod");

        DatabaseEnvironmentValidator validator = new DatabaseEnvironmentValidator(env, securityProps);
        ReflectionTestUtils.setField(validator, "datasourceUrl", "jdbc:postgresql://db.supabase.co:5432/postgres");

        assertThatCode(validator::validateDatabaseConfiguration).doesNotThrowAnyException();
    }
}
