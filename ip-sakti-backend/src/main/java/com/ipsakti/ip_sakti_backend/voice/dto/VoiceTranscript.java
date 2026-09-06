package com.ipsakti.ip_sakti_backend.voice.dto;

import com.ipsakti.ip_sakti_backend.question.model.Language;

public record VoiceTranscript(String text, Language language) {}
