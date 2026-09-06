package com.ipsakti.ip_sakti_backend.question.general;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.ipsakti.ip_sakti_backend.config.GeminiProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GeminiGeneralLlmProviderTest {
    @Test
    void fallsBackAcrossModelsAndKeepsApiKeyOutOfUrl() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://gemini.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GeminiProperties properties = new GeminiProperties();
        properties.setApiKey("test-key");
        properties.setBaseUrl("https://gemini.test");
        properties.setModel("missing-model");
        properties.setFallbackModels("working-model");
        GeminiGeneralLlmProvider provider = new GeminiGeneralLlmProvider(builder.build(), properties);

        server.expect(once(), requestTo("https://gemini.test/v1beta/models/missing-model:generateContent"))
                .andExpect(header("x-goog-api-key", "test-key"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON).body("{}"));
        server.expect(once(), requestTo("https://gemini.test/v1beta/models/working-model:generateContent"))
                .andExpect(header("x-goog-api-key", "test-key"))
                .andRespond(withSuccess("""
                        {"candidates":[{"content":{"parts":[{"text":"Hello!"}]}}]}
                        """, MediaType.APPLICATION_JSON));

        assertThat(provider.answer("Hi")).isEqualTo("Hello!");
        server.verify();
    }
}
