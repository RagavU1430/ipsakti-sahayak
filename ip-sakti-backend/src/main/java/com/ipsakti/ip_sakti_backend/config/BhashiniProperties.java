package com.ipsakti.ip_sakti_backend.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "bhashini")
public class BhashiniProperties {

    private boolean enabled;
    private String baseUrl = "";
    private String apiKey = "";
    private String userId = "";
    private String translationServiceId = "";
    private String pipelineId = "";
    private Duration connectTimeout = Duration.ofSeconds(2);
    private Duration readTimeout = Duration.ofSeconds(15);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getTranslationServiceId() {
        return translationServiceId;
    }

    public void setTranslationServiceId(String translationServiceId) {
        this.translationServiceId = translationServiceId;
    }

    public String getPipelineId() {
        return pipelineId;
    }

    public void setPipelineId(String pipelineId) {
        this.pipelineId = pipelineId;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }

    public boolean configured() {
        return enabled
                && baseUrl != null && !baseUrl.isBlank()
                && apiKey != null && !apiKey.isBlank()
                && translationServiceId != null && !translationServiceId.isBlank()
                && pipelineId != null && !pipelineId.isBlank();
    }
}
