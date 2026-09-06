package com.ipsakti.ip_sakti_backend.multilingual;

import com.ipsakti.ip_sakti_backend.question.model.Language;

/**
 * Abstraction for translation providers.
 * Implementation: GeminiTranslationProvider (ONLY).
 */
public interface TranslationProvider {

    String translate(String text, Language sourceLanguage, Language targetLanguage);

    default void invalidate(String text, Language sourceLanguage, Language targetLanguage) {
        // Providers without a cache do not need to do anything.
    }

    boolean isConfigured();

    String providerName();
}
