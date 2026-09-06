package com.ipsakti.ip_sakti_backend.voice.provider;

import com.ipsakti.ip_sakti_backend.config.GeminiProperties;
import com.ipsakti.ip_sakti_backend.question.model.Language;
import com.ipsakti.ip_sakti_backend.voice.config.VoiceProperties;
import com.ipsakti.ip_sakti_backend.voice.dto.SynthesizedSpeech;
import com.ipsakti.ip_sakti_backend.voice.exception.VoiceException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class GeminiTextToSpeechProvider implements TextToSpeechProvider {
    private static final Logger log = LoggerFactory.getLogger(GeminiTextToSpeechProvider.class);
    private final RestClient client;
    private final GeminiProperties gemini;
    private final VoiceProperties voice;

    public GeminiTextToSpeechProvider(@Qualifier("voiceGeminiRestClient") RestClient client, GeminiProperties gemini, VoiceProperties voice) {
        this.client = client;
        this.gemini = gemini;
        this.voice = voice;
    }

    @Override
    public SynthesizedSpeech synthesize(String text, Language language) {
        if (!isConfigured()) throw VoiceException.providerUnavailable();
        long started = System.nanoTime();
        try {
            Map<String, Object> speechConfig = Map.of(
                    "languageCode", locale(language),
                    "voiceConfig", Map.of("prebuiltVoiceConfig", Map.of("voiceName", voice.getTtsVoice())));
            Map<String, Object> body = Map.of(
                    "contents", List.of(Map.of("parts", List.of(Map.of("text", "Read this answer clearly and exactly:\n" + text)))),
                    "generationConfig", Map.of("responseModalities", List.of("AUDIO"), "speechConfig", speechConfig));
            Map<?, ?> response = client.post()
                    .uri("/v1beta/models/{model}:generateContent", voice.getTtsModel())
                    .header("x-goog-api-key", gemini.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            AudioPart part = extractAudio(response);
            byte[] decoded = Base64.getDecoder().decode(part.data());
            String mime = part.mimeType() == null ? "audio/L16;codec=pcm;rate=24000" : part.mimeType();
            if (mime.toLowerCase().contains("l16") || mime.toLowerCase().contains("pcm")) {
                decoded = PcmWaveEncoder.mono16Bit24Khz(decoded);
                mime = "audio/wav";
            }
            if (decoded.length == 0) throw VoiceException.ttsFailed();
            log.info("voice_tts_success model={} language={} bytes={} latencyMs={}", voice.getTtsModel(), language, decoded.length, elapsedMs(started));
            return new SynthesizedSpeech(decoded, mime);
        } catch (RestClientResponseException ex) {
            log.warn("voice_tts_provider_error model={} status={} latencyMs={}", voice.getTtsModel(), ex.getStatusCode().value(), elapsedMs(started));
            if (ex.getStatusCode().value() == 401 || ex.getStatusCode().value() == 403) throw VoiceException.providerUnavailable();
            throw VoiceException.ttsFailed();
        } catch (ResourceAccessException ex) {
            log.warn("voice_tts_timeout model={} latencyMs={}", voice.getTtsModel(), elapsedMs(started));
            throw VoiceException.timeout();
        } catch (RestClientException ex) {
            if (hasTimeoutCause(ex)) {
                log.warn("voice_tts_timeout model={} latencyMs={}", voice.getTtsModel(), elapsedMs(started));
                throw VoiceException.timeout();
            }
            log.warn("voice_tts_malformed_response model={} latencyMs={}", voice.getTtsModel(), elapsedMs(started));
            throw VoiceException.ttsFailed();
        } catch (IllegalArgumentException ex) {
            throw VoiceException.ttsFailed();
        }
    }

    private AudioPart extractAudio(Map<?, ?> response) {
        try {
            List<?> candidates = (List<?>) response.get("candidates");
            Map<?, ?> candidate = (Map<?, ?>) candidates.get(0);
            Map<?, ?> content = (Map<?, ?>) candidate.get("content");
            List<?> parts = (List<?>) content.get("parts");
            Map<?, ?> part = (Map<?, ?>) parts.get(0);
            Map<?, ?> inline = (Map<?, ?>) part.get("inlineData");
            Object data = inline.get("data");
            Object mime = inline.get("mimeType");
            if (data == null) throw VoiceException.ttsFailed();
            return new AudioPart(data.toString(), mime == null ? null : mime.toString());
        } catch (RuntimeException ex) {
            if (ex instanceof VoiceException voiceException) throw voiceException;
            throw VoiceException.ttsFailed();
        }
    }

    private String locale(Language language) {
        return switch (language) {
            case EN -> "en-IN"; case HI -> "hi-IN"; case TA -> "ta-IN";
            case TE -> "te-IN"; case KN -> "kn-IN"; case ML -> "ml-IN";
        };
    }

    private long elapsedMs(long started) { return (System.nanoTime() - started) / 1_000_000; }
    private boolean hasTimeoutCause(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof java.net.SocketTimeoutException) return true;
        }
        return false;
    }
    @Override public boolean isConfigured() { return gemini.configured() && voice.getTtsModel() != null && !voice.getTtsModel().isBlank(); }
    @Override public String providerName() { return "gemini-tts"; }
    private record AudioPart(String data, String mimeType) {}
}
