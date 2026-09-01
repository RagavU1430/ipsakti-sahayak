package com.ipsakti.ip_sakti_backend.multilingual;

import com.ipsakti.ip_sakti_backend.question.model.Language;

public record LanguageMetadata(
        Language requestedLanguage,
        Language detectedLanguage,
        Language processingLanguage
) {
}
