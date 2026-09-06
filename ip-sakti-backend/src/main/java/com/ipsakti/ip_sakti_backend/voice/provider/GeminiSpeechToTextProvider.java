package com.ipsakti.ip_sakti_backend.voice.provider;

import com.ipsakti.ip_sakti_backend.config.GeminiProperties;
import com.ipsakti.ip_sakti_backend.question.model.Language;
import com.ipsakti.ip_sakti_backend.voice.dto.VoiceTranscript;
import com.ipsakti.ip_sakti_backend.voice.exception.VoiceException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
public class GeminiSpeechToTextProvider implements SpeechToTextProvider {
    private static final Logger log = LoggerFactory.getLogger(GeminiSpeechToTextProvider.class);
    private final RestClient client;
    private final GeminiProperties properties;
    private final Set<String> unavailableModels = ConcurrentHashMap.newKeySet();

    public GeminiSpeechToTextProvider(@Qualifier("voiceGeminiRestClient") RestClient client, GeminiProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public VoiceTranscript transcribe(byte[] audio, String mimeType, Language language) {
        if (!isConfigured()) throw VoiceException.providerUnavailable();
        VoiceException last = null;
        for (String model : properties.modelCandidates()) {
            if (unavailableModels.contains(model)) continue;
            long started = System.nanoTime();
            try {
                Map<String, Object> body = Map.of(
                        "contents", List.of(Map.of("parts", List.of(
                                Map.of("inlineData", Map.of("mimeType", normalizeMime(mimeType), "data", Base64.getEncoder().encodeToString(audio))),
                                Map.of("text", transcriptionPrompt(language))))),
                        "generationConfig", Map.of("temperature", 0, "maxOutputTokens", 1000));
                Map<?, ?> response = client.post()
                        .uri("/v1beta/models/{model}:generateContent", model)
                        .header("x-goog-api-key", properties.getApiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(Map.class);
                String text = extractText(response);
                if (text == null || text.isBlank() || "NO_SPEECH".equalsIgnoreCase(text.trim())) {
                    throw VoiceException.sttEmpty();
                }
                log.info("voice_stt_success model={} latencyMs={}", model, elapsedMs(started));
                return new VoiceTranscript(text.trim(), language);
            } catch (RestClientResponseException ex) {
                int status = ex.getStatusCode().value();
                log.warn("voice_stt_provider_error model={} status={} latencyMs={}", model, status, elapsedMs(started));
                if (status == 401 || status == 403) throw VoiceException.providerUnavailable();
                if (status == 404) unavailableModels.add(model);
                last = VoiceException.sttFailed();
            } catch (ResourceAccessException ex) {
                log.warn("voice_stt_network_error model={} latencyMs={}", model, elapsedMs(started));
                last = VoiceException.timeout();
            } catch (RestClientException ex) {
                log.warn("voice_stt_response_error model={} latencyMs={}", model, elapsedMs(started));
                last = hasTimeoutCause(ex) ? VoiceException.timeout() : VoiceException.sttFailed();
            } catch (VoiceException ex) {
                throw ex;
            }
        }
        throw last == null ? VoiceException.providerUnavailable() : last;
    }

    private String transcriptionPrompt(Language language) {
        return "Transcribe only the human speech verbatim. Language hint: " + language.toJson()
                + ". Preserve legal names and section identifiers. Use native script for Indic speech. "
                + "If there is no discernible speech output only NO_SPEECH.";
    }

    private String normalizeMime(String mimeType) {
        if (mimeType == null || mimeType.isBlank()) return "audio/webm";
        String clean = mimeType.split(";")[0].trim().toLowerCase();
        if (clean.equals("audio/x-wav") || clean.equals("audio/wave")) return "audio/wav";
        if (clean.equals("audio/mp3")) return "audio/mpeg";
        if (clean.equals("audio/m4a")) return "audio/mp4";
        return clean;
    }

    @SuppressWarnings("unchecked")
    private String extractText(Map<?, ?> response) {
        if (response == null) return null;
        Object candidatesValue = response.get("candidates");
        if (!(candidatesValue instanceof List<?> candidates) || candidates.isEmpty()) return null;
        Object firstValue = candidates.get(0);
        if (!(firstValue instanceof Map<?, ?> first)) return null;
        Object contentValue = first.get("content");
        if (!(contentValue instanceof Map<?, ?> content)) return null;
        Object partsValue = content.get("parts");
        if (!(partsValue instanceof List<?> parts) || parts.isEmpty()) return null;
        Object partValue = parts.get(0);
        if (!(partValue instanceof Map<?, ?> part)) return null;
        Object text = part.get("text");
        return text == null ? null : text.toString();
    }

    private long elapsedMs(long started) { return (System.nanoTime() - started) / 1_000_000; }
    private boolean hasTimeoutCause(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof java.net.SocketTimeoutException) return true;
        }
        return false;
    }
    @Override public boolean isConfigured() { return properties.configured(); }
    @Override public String providerName() { return "gemini-multimodal"; }
}
