package com.ipsakti.ip_sakti_backend.voice;

import com.ipsakti.ip_sakti_backend.auth.UserPrincipal;
import com.ipsakti.ip_sakti_backend.question.model.Jurisdiction;
import com.ipsakti.ip_sakti_backend.question.model.Language;
import com.ipsakti.ip_sakti_backend.voice.config.VoiceProperties;
import com.ipsakti.ip_sakti_backend.voice.dto.VoiceAskResponse;
import com.ipsakti.ip_sakti_backend.voice.dto.SynthesizeSpeechRequest;
import com.ipsakti.ip_sakti_backend.voice.dto.SynthesizedSpeech;
import com.ipsakti.ip_sakti_backend.voice.exception.VoiceException;
import com.ipsakti.ip_sakti_backend.voice.provider.SpeechToTextProvider;
import com.ipsakti.ip_sakti_backend.voice.provider.TextToSpeechProvider;
import java.io.IOException;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestController
@RequestMapping("/api/v1/voice")
public class VoiceController {
    private final VoiceService service;
    private final VoiceProperties properties;
    private final SpeechToTextProvider stt;
    private final TextToSpeechProvider tts;

    public VoiceController(VoiceService service, VoiceProperties properties, SpeechToTextProvider stt, TextToSpeechProvider tts) {
        this.service = service;
        this.properties = properties;
        this.stt = stt;
        this.tts = tts;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", stt.isConfigured() && tts.isConfigured() ? "UP" : "DEGRADED",
                "sttProvider", stt.providerName(), "ttsProvider", tts.providerName(),
                "sttConfigured", stt.isConfigured(), "ttsConfigured", tts.isConfigured(),
                "maxAudioBytes", properties.getMaxAudioBytes(),
                "allowedMimeTypes", properties.getAllowedMimeTypes(),
                "supportedLanguages", new String[]{"en", "hi", "ta", "te", "kn", "ml"});
    }

    @PostMapping(value = "/ask", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public VoiceAskResponse ask(
            @RequestParam("audio") MultipartFile audio,
            @RequestParam(value = "language", defaultValue = "en") String language,
            @RequestParam(value = "jurisdiction", defaultValue = "INDIA") String jurisdiction,
            @RequestParam(value = "conversationId", required = false) String conversationId,
            @AuthenticationPrincipal UserPrincipal principal) {
        if (audio == null || audio.isEmpty()) throw VoiceException.noAudio();
        try {
            return service.ask(audio.getBytes(), audio.getContentType(), parseLanguage(language),
                    parseJurisdiction(jurisdiction), conversationId, principal);
        } catch (IOException ex) {
            throw VoiceException.of("RECORDING_FAILED", "The uploaded recording could not be read.", org.springframework.http.HttpStatus.BAD_REQUEST);
        }
    }

    @PostMapping(value = "/synthesize", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> synthesize(@org.springframework.web.bind.annotation.RequestBody SynthesizeSpeechRequest request) {
        Language language = parseLanguage(request == null ? null : request.language());
        SynthesizedSpeech speech = service.synthesize(request == null ? null : request.text(), language);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(speech.mimeType() == null ? "audio/wav" : speech.mimeType()))
                .body(speech.bytes());
    }

    private Language parseLanguage(String value) {
        try { return Language.fromJson(value); }
        catch (RuntimeException ex) {
            throw VoiceException.of("INVALID_LANGUAGE", "Supported languages are en, hi, ta, te, kn, and ml.", org.springframework.http.HttpStatus.BAD_REQUEST);
        }
    }

    private Jurisdiction parseJurisdiction(String value) {
        try { return Jurisdiction.fromJson(value); }
        catch (RuntimeException ex) {
            throw VoiceException.of("INVALID_JURISDICTION", "Supported jurisdictions are INDIA, INTERNATIONAL, and AUTO.", org.springframework.http.HttpStatus.BAD_REQUEST);
        }
    }

    @ExceptionHandler(VoiceException.class)
    public ResponseEntity<Map<String, Object>> voiceError(VoiceException ex) {
        return ResponseEntity.status(ex.getStatus()).body(Map.of("code", ex.getCode(), "error", ex.getMessage(), "status", ex.getStatus().value()));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<Map<String, Object>> missingAudio(MissingServletRequestPartException ex) {
        return ResponseEntity.badRequest().body(Map.of(
                "code", "AUDIO_REQUIRED",
                "error", "A non-empty audio recording is required.",
                "status", 400));
    }
}
