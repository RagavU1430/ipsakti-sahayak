package com.ipsakti.ip_sakti_backend.voice.provider;

import com.ipsakti.ip_sakti_backend.question.model.Language;
import com.ipsakti.ip_sakti_backend.voice.dto.VoiceTranscript;

public interface SpeechToTextProvider {
    VoiceTranscript transcribe(byte[] audio, String mimeType, Language language);
    boolean isConfigured();
    String providerName();
}
