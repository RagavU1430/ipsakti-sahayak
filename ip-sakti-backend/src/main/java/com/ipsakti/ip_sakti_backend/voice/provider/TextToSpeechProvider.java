package com.ipsakti.ip_sakti_backend.voice.provider;

import com.ipsakti.ip_sakti_backend.question.model.Language;
import com.ipsakti.ip_sakti_backend.voice.dto.SynthesizedSpeech;

public interface TextToSpeechProvider {
    SynthesizedSpeech synthesize(String text, Language language);
    boolean isConfigured();
    String providerName();
}
