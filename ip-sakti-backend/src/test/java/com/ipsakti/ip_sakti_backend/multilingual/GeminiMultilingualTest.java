package com.ipsakti.ip_sakti_backend.multilingual;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.ipsakti.ip_sakti_backend.config.GeminiProperties;
import com.ipsakti.ip_sakti_backend.question.model.Language;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GeminiMultilingualTest {

    @Test
    void languageRegistrySupportsExactlyTheSixSupportedLanguages() {
        assertThat(Language.values()).containsExactly(Language.EN, Language.HI, Language.TA, Language.TE, Language.KN, Language.ML);
        assertThat(Language.fromJson("EN")).isEqualTo(Language.EN);
        assertThat(Language.fromJson("ta")).isEqualTo(Language.TA);
        assertThatThrownBy(() -> Language.fromJson("fr")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void detectsEverySupportedIndicScript() {
        TranslationService service = new TranslationService(new NoopProvider());
        assertThat(service.detect("What is a patent?")).isEqualTo(Language.EN);
        assertThat(service.detect("भारत में पेटेंट क्या है?")).isEqualTo(Language.HI);
        assertThat(service.detect("இந்தியாவில் காப்புரிமை என்றால் என்ன?")).isEqualTo(Language.TA);
        assertThat(service.detect("భారతదేశంలో పేటెంట్ అంటే ఏమిటి?")).isEqualTo(Language.TE);
        assertThat(service.detect("ಭಾರತದಲ್ಲಿ ಪೇಟೆಂಟ್ ಎಂದರೇನು?")).isEqualTo(Language.KN);
        assertThat(service.detect("ഇന്ത്യയിൽ പേറ്റന്റ് എന്താണ്?")).isEqualTo(Language.ML);
    }

    @Test
    void geminiProviderFallsBackWhenTheFirstModelIsUnavailable() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://gemini.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GeminiProperties properties = new GeminiProperties();
        properties.setApiKey("test-gemini-key");
        properties.setBaseUrl("https://gemini.test");
        properties.setModel("gemini-missing-model");
        properties.setFallbackModels("gemini-2.5-flash");
        GeminiTranslationProvider provider = new GeminiTranslationProvider(builder.build(), properties);

        server.expect(requestTo("https://gemini.test/v1beta/models/gemini-missing-model:generateContent?key=test-gemini-key"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON).body("{}"));
        server.expect(requestTo("https://gemini.test/v1beta/models/gemini-2.5-flash:generateContent?key=test-gemini-key"))
                .andRespond(withSuccess("""
                        {"candidates":[{"content":{"parts":[{"text":"What is a patent?"}]}}]}
                        """, MediaType.APPLICATION_JSON));

        assertThat(provider.translate("भारत में पेटेंट क्या है?", Language.HI, Language.EN)).isEqualTo("What is a patent?");
        server.verify();
    }

    private static final class NoopProvider implements TranslationProvider {
        @Override public String translate(String text, Language sourceLanguage, Language targetLanguage) { return text; }
        @Override public boolean isConfigured() { return true; }
        @Override public String providerName() { return "test"; }
    }
}
