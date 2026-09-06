package com.ipsakti.ip_sakti_backend.voice.config;

import com.ipsakti.ip_sakti_backend.config.GeminiProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class VoiceClientConfig {
    @Bean("voiceGeminiRestClient")
    RestClient voiceGeminiRestClient(RestClient.Builder builder, GeminiProperties gemini, VoiceProperties voice) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) gemini.getConnectTimeout().toMillis());
        factory.setReadTimeout((int) voice.getRequestTimeout().toMillis());
        return builder.requestFactory(factory).baseUrl(gemini.getBaseUrl()).build();
    }
}
