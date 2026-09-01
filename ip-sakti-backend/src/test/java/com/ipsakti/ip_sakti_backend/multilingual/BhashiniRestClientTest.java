package com.ipsakti.ip_sakti_backend.multilingual;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ipsakti.ip_sakti_backend.config.BhashiniProperties;
import com.ipsakti.ip_sakti_backend.exception.BhashiniClientException;
import com.ipsakti.ip_sakti_backend.question.model.Language;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class BhashiniRestClientTest {

    @Test
    void disabledConfigurationFailsSafelyWithoutNetworkCall() {
        BhashiniRestClient client = new BhashiniRestClient(RestClient.builder().baseUrl("https://example.invalid").build(), new BhashiniProperties());

        assertThatThrownBy(() -> client.translate("வணக்கம்", Language.TA, Language.EN))
                .isInstanceOf(BhashiniClientException.class)
                .hasMessageContaining("not configured");
    }

    @Test
    void mapsSuccessfulTranslationResponse() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://bhashini.example");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        BhashiniRestClient client = new BhashiniRestClient(builder.build(), configuredProperties());

        server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo("https://bhashini.example/translate"))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.method(HttpMethod.POST))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess(
                        "{\"translatedText\":\"What is a patent?\"}",
                        org.springframework.http.MediaType.APPLICATION_JSON
                ));

        String translated = client.translate("காப்புரிமை என்றால் என்ன?", Language.TA, Language.EN);

        assertThat(translated).isEqualTo("What is a patent?");
        server.verify();
    }

    @Test
    void mapsBhashiniHttpFailureToControlledException() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://bhashini.example");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        BhashiniRestClient client = new BhashiniRestClient(builder.build(), configuredProperties());

        server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo("https://bhashini.example/translate"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withStatus(HttpStatus.BAD_GATEWAY));

        assertThatThrownBy(() -> client.translate("வணக்கம்", Language.TA, Language.EN))
                .isInstanceOf(BhashiniClientException.class)
                .hasMessageContaining("HTTP 502");
    }

    @Test
    void mapsMalformedBhashiniResponseToControlledException() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://bhashini.example");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        BhashiniRestClient client = new BhashiniRestClient(builder.build(), configuredProperties());

        server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo("https://bhashini.example/translate"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess(
                        "{\"unexpected\":\"shape\"}",
                        org.springframework.http.MediaType.APPLICATION_JSON
                ));

        assertThatThrownBy(() -> client.translate("வணக்கம்", Language.TA, Language.EN))
                .isInstanceOf(BhashiniClientException.class)
                .hasMessageContaining("invalid response");
    }

    @Test
    void mapsBhashiniTimeoutToControlledException() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://bhashini.example");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        BhashiniRestClient client = new BhashiniRestClient(builder.build(), configuredProperties());

        server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo("https://bhashini.example/translate"))
                .andRespond(request -> {
                    throw new IOException("Read timed out");
                });

        assertThatThrownBy(() -> client.translate("வணக்கம்", Language.TA, Language.EN))
                .isInstanceOf(BhashiniClientException.class)
                .hasMessageContaining("timed out");
    }

    private BhashiniProperties configuredProperties() {
        BhashiniProperties properties = new BhashiniProperties();
        properties.setEnabled(true);
        properties.setBaseUrl("https://bhashini.example");
        properties.setApiKey("test-key");
        properties.setTranslationServiceId("test-service");
        properties.setPipelineId("test-pipeline");
        return properties;
    }
}
