package com.ipsakti.ip_sakti_backend.multilingual;

public record TranslatedText(
        String originalText,
        String canonicalText,
        LanguageMetadata metadata
) {
}
