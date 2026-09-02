package com.ipsakti.ip_sakti_backend.multilingual;

import com.ipsakti.ip_sakti_backend.question.model.Language;

/**
 * Abstraction for translation providers.
 * Implementation: GeminiTranslationProvider (ONLY).
 */
public interface TranslationProvider {

    String translate(String text, Language sourceLanguage, Language targetLanguage);

    boolean isConfigured();

    String providerName();
}
