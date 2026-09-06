package com.ipsakti.ip_sakti_backend.voice.exception;

import org.springframework.http.HttpStatus;

public class VoiceException extends RuntimeException {
    private final String code;
    private final HttpStatus status;

    public VoiceException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String getCode() { return code; }
    public HttpStatus getStatus() { return status; }

    public static VoiceException of(String code, String message, HttpStatus status) {
        return new VoiceException(code, message, status);
    }
    public static VoiceException noAudio() { return of("AUDIO_EMPTY", "No audio was captured.", HttpStatus.BAD_REQUEST); }
    public static VoiceException tooLarge() { return of("AUDIO_TOO_LARGE", "The recording exceeds the configured size limit.", HttpStatus.PAYLOAD_TOO_LARGE); }
    public static VoiceException unsupportedAudio() { return of("UNSUPPORTED_AUDIO", "The recording format is not supported.", HttpStatus.UNSUPPORTED_MEDIA_TYPE); }
    public static VoiceException sttEmpty() { return of("STT_EMPTY", "No discernible speech was detected.", HttpStatus.BAD_REQUEST); }
    public static VoiceException sttFailed() { return of("STT_FAILED", "Speech transcription failed.", HttpStatus.BAD_GATEWAY); }
    public static VoiceException ttsFailed() { return of("TTS_FAILED", "Answer audio generation failed.", HttpStatus.BAD_GATEWAY); }
    public static VoiceException providerUnavailable() { return of("VOICE_PROVIDER_UNAVAILABLE", "The voice provider is unavailable.", HttpStatus.SERVICE_UNAVAILABLE); }
    public static VoiceException timeout() { return of("TIMEOUT", "Voice processing timed out.", HttpStatus.GATEWAY_TIMEOUT); }
}
