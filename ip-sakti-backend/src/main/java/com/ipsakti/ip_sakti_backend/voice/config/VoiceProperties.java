package com.ipsakti.ip_sakti_backend.voice.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "voice")
public class VoiceProperties {
    private boolean enabled = true;
    private long maxAudioBytes = 10 * 1024 * 1024;
    private Duration requestTimeout = Duration.ofSeconds(90);
    private String ttsModel = "gemini-3.1-flash-tts-preview";
    private List<String> ttsFallbackModels = List.of("gemini-2.5-flash-preview-tts");
    private int ttsMaxAttempts = 2;
    private String ttsVoice = "Kore";
    private List<String> allowedMimeTypes = List.of(
            "audio/webm", "audio/wav", "audio/x-wav", "audio/wave",
            "audio/ogg", "audio/mpeg", "audio/mp3", "audio/mp4",
            "audio/m4a", "audio/aac", "audio/flac");

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public long getMaxAudioBytes() { return maxAudioBytes; }
    public void setMaxAudioBytes(long maxAudioBytes) { this.maxAudioBytes = maxAudioBytes; }
    public Duration getRequestTimeout() { return requestTimeout; }
    public void setRequestTimeout(Duration requestTimeout) { this.requestTimeout = requestTimeout; }
    public String getTtsModel() { return ttsModel; }
    public void setTtsModel(String ttsModel) { this.ttsModel = ttsModel; }
    public String getTtsVoice() { return ttsVoice; }
    public void setTtsVoice(String ttsVoice) { this.ttsVoice = ttsVoice; }
    public List<String> getTtsFallbackModels() { return ttsFallbackModels; }
    public void setTtsFallbackModels(List<String> ttsFallbackModels) { this.ttsFallbackModels = ttsFallbackModels; }
    public int getTtsMaxAttempts() { return ttsMaxAttempts; }
    public void setTtsMaxAttempts(int ttsMaxAttempts) { this.ttsMaxAttempts = ttsMaxAttempts; }
    public List<String> ttsModelCandidates() {
        java.util.LinkedHashSet<String> candidates = new java.util.LinkedHashSet<>();
        if (ttsModel != null && !ttsModel.isBlank()) candidates.add(ttsModel.trim());
        if (ttsFallbackModels != null) {
            ttsFallbackModels.stream().filter(java.util.Objects::nonNull).map(String::trim)
                    .filter(model -> !model.isBlank()).forEach(candidates::add);
        }
        int limit = Math.max(1, Math.min(ttsMaxAttempts, 2));
        return candidates.stream().limit(limit).toList();
    }
    public List<String> getAllowedMimeTypes() { return allowedMimeTypes; }
    public void setAllowedMimeTypes(List<String> allowedMimeTypes) { this.allowedMimeTypes = allowedMimeTypes; }
}
