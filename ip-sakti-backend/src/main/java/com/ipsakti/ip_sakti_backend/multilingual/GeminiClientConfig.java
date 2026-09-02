package com.ipsakti.ip_sakti_backend.multilingual;

import com.ipsakti.ip_sakti_backend.config.GeminiProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class GeminiClientConfig {

    @Bean("geminiRestClient")
    RestClient geminiRestClient(RestClient.Builder builder, GeminiProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) properties.getConnectTimeout().toMillis());
        requestFactory.setReadTimeout((int) properties.getReadTimeout().toMillis());
        RestClient.Builder configured = builder.requestFactory(requestFactory);
        if (properties.getBaseUrl() != null && !properties.getBaseUrl().isBlank()) {
            configured = configured.baseUrl(properties.getBaseUrl());
        }
        return configured.build();
    }

    @Bean
    TranslationProvider geminiTranslationProvider(RestClient geminiRestClient, GeminiProperties properties) {
        return new GeminiTranslationProvider(geminiRestClient, properties);
    }
}
