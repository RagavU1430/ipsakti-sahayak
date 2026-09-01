package com.ipsakti.ip_sakti_backend.multilingual;

import com.ipsakti.ip_sakti_backend.config.BhashiniProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class BhashiniClientConfig {

    @Bean("bhashiniRestClient")
    RestClient bhashiniRestClient(RestClient.Builder builder, BhashiniProperties properties) {
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
    BhashiniClient bhashiniClient(
            @Qualifier("bhashiniRestClient") RestClient restClient,
            BhashiniProperties properties
    ) {
        return new BhashiniRestClient(restClient, properties);
    }
}
