package com.ipsakti.ip_sakti_backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class PropertiesBindingTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfig.class);

    @Test
    void ragPropertiesLoadFromEnvironmentStyleProperties() {
        contextRunner
                .withPropertyValues(
                        "rag.base-url=http://localhost:9000",
                        "rag.connect-timeout=3s",
                        "rag.read-timeout=30s"
                )
                .run(context -> {
                    RagProperties properties = context.getBean(RagProperties.class);
                    assertThat(properties.getBaseUrl()).isEqualTo("http://localhost:9000");
                    assertThat(properties.getConnectTimeout()).isEqualTo(Duration.ofSeconds(3));
                    assertThat(properties.getReadTimeout()).isEqualTo(Duration.ofSeconds(30));
                });
    }

    @Test
    void securityPropertiesLoadAllowedOriginsAndMode() {
        contextRunner
                .withPropertyValues(
                        "app.security.mode=prod",
                        "app.security.api-key=test-key",
                        "app.security.allowed-origins=http://localhost:3000,http://localhost:5173"
                )
                .run(context -> {
                    SecurityProperties properties = context.getBean(SecurityProperties.class);
                    assertThat(properties.apiKeyRequired()).isTrue();
                    assertThat(properties.getApiKey()).isEqualTo("test-key");
                    assertThat(properties.getAllowedOrigins()).containsExactly("http://localhost:3000", "http://localhost:5173");
                });
    }

    @Test
    void futureIntegrationPropertiesLoadFromEnvironmentStyleProperties() {
        contextRunner
                .withPropertyValues(
                        "app.jwt.issuer=ip-sakti-test",
                        "app.jwt.secret=test-secret",
                        "app.jwt.access-token-ttl=45m",
                        "bhashini.enabled=true",
                        "bhashini.base-url=https://example.invalid/bhashini",
                        "bhashini.api-key=test-bhashini-key"
                )
                .run(context -> {
                    JwtProperties jwtProperties = context.getBean(JwtProperties.class);
                    BhashiniProperties bhashiniProperties = context.getBean(BhashiniProperties.class);

                    assertThat(jwtProperties.getIssuer()).isEqualTo("ip-sakti-test");
                    assertThat(jwtProperties.getSecret()).isEqualTo("test-secret");
                    assertThat(jwtProperties.getAccessTokenTtl()).isEqualTo(Duration.ofMinutes(45));
                    assertThat(bhashiniProperties.isEnabled()).isTrue();
                    assertThat(bhashiniProperties.getBaseUrl()).isEqualTo("https://example.invalid/bhashini");
                    assertThat(bhashiniProperties.getApiKey()).isEqualTo("test-bhashini-key");
                });
    }

    @EnableConfigurationProperties({
            BhashiniProperties.class,
            JwtProperties.class,
            RagProperties.class,
            SupabaseProperties.class,
            SecurityProperties.class
    })
    static class PropertiesConfig {
    }
}
