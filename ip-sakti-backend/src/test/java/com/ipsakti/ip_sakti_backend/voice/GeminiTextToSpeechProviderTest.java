package com.ipsakti.ip_sakti_backend.voice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.ipsakti.ip_sakti_backend.config.GeminiProperties;
import com.ipsakti.ip_sakti_backend.question.model.Language;
import com.ipsakti.ip_sakti_backend.voice.config.VoiceProperties;
import com.ipsakti.ip_sakti_backend.voice.exception.VoiceException;
import com.ipsakti.ip_sakti_backend.voice.provider.GeminiTextToSpeechProvider;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GeminiTextToSpeechProviderTest {

    @Test
    void retriesOnceWithConfiguredFallbackAfterRateLimitAndReturnsPlayableWave() {
        Fixture fixture = fixture();
        fixture.server.expect(requestTo("https://gemini.test/v1beta/models/primary-tts:generateContent"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        fixture.server.expect(requestTo("https://gemini.test/v1beta/models/fallback-tts:generateContent"))
                .andRespond(withSuccess(successfulPcmResponse(), MediaType.APPLICATION_JSON));

        var result = fixture.provider.synthesize("வணக்கம்", Language.TA);

        assertThat(result.mimeType()).isEqualTo("audio/wav");
        assertThat(new String(result.bytes(), 0, 4, StandardCharsets.US_ASCII)).isEqualTo("RIFF");
        assertThat(result.bytes()).hasSize(48);
        fixture.server.verify();
    }

    @Test
    void stopsAfterTwoRetryableFailuresAndReturnsExplicitUnavailableError() {
        Fixture fixture = fixture();
        fixture.server.expect(requestTo("https://gemini.test/v1beta/models/primary-tts:generateContent"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        fixture.server.expect(requestTo("https://gemini.test/v1beta/models/fallback-tts:generateContent"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> fixture.provider.synthesize("നമസ്കാരം", Language.ML))
                .isInstanceOfSatisfying(VoiceException.class,
                        error -> assertThat(error.getCode()).isEqualTo("TTS_UNAVAILABLE"));
        fixture.server.verify();
    }

    @Test
    void modelCandidatesAreDeduplicatedAndHardLimitedToTwo() {
        VoiceProperties voice = new VoiceProperties();
        voice.setTtsModel("primary-tts");
        voice.setTtsFallbackModels(List.of("primary-tts", "fallback-tts", "third-tts"));
        voice.setTtsMaxAttempts(99);

        assertThat(voice.ttsModelCandidates()).containsExactly("primary-tts", "fallback-tts");
    }

    private Fixture fixture() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://gemini.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GeminiProperties gemini = new GeminiProperties();
        gemini.setApiKey("test-key");
        gemini.setBaseUrl("https://gemini.test");
        VoiceProperties voice = new VoiceProperties();
        voice.setTtsModel("primary-tts");
        voice.setTtsFallbackModels(List.of("fallback-tts", "third-tts"));
        voice.setTtsMaxAttempts(2);
        return new Fixture(server, new GeminiTextToSpeechProvider(builder.build(), gemini, voice));
    }

    private String successfulPcmResponse() {
        return """
                {"candidates":[{"content":{"parts":[{"inlineData":{
                  "data":"AQIDBA==","mimeType":"audio/L16;codec=pcm;rate=24000"
                }}]}}]}
                """;
    }

    private record Fixture(MockRestServiceServer server, GeminiTextToSpeechProvider provider) {}
}
