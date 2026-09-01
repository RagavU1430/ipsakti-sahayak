package com.ipsakti.ip_sakti_backend.multilingual;

import com.ipsakti.ip_sakti_backend.question.model.Language;
import com.ipsakti.ip_sakti_backend.exception.BhashiniClientException;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TranslationService {

    private static final Logger log = LoggerFactory.getLogger(TranslationService.class);
    private static final Language CANONICAL_LANGUAGE = Language.EN;

    private final BhashiniClient bhashiniClient;

    public TranslationService(BhashiniClient bhashiniClient) {
        this.bhashiniClient = bhashiniClient;
    }

    public TranslatedText toCanonical(String text, Language requestedLanguage, String requestId) {
        Language requested = requestedLanguage == null ? detect(text) : requestedLanguage;
        Language detected = detect(text);
        if (requested == CANONICAL_LANGUAGE) {
            return new TranslatedText(text, text, new LanguageMetadata(requested, detected, CANONICAL_LANGUAGE));
        }

        long started = System.nanoTime();
        String translated = bhashiniClient.translate(text, requested, CANONICAL_LANGUAGE);
        if (translated == null || translated.isBlank()) {
            throw BhashiniClientException.malformedResponse();
        }
        log.info(
                "translation_to_canonical requestId={} requestedLanguage={} detectedLanguage={} processingLanguage={} latencyMs={}",
                requestId,
                requested,
                detected,
                CANONICAL_LANGUAGE,
                Duration.ofNanos(System.nanoTime() - started).toMillis()
        );
        return new TranslatedText(text, translated, new LanguageMetadata(requested, detected, CANONICAL_LANGUAGE));
    }

    public String fromCanonical(String text, LanguageMetadata metadata, String requestId) {
        if (text == null || metadata.requestedLanguage() == CANONICAL_LANGUAGE) {
            return text;
        }

        long started = System.nanoTime();
        String translated = bhashiniClient.translate(text, CANONICAL_LANGUAGE, metadata.requestedLanguage());
        if (translated == null || translated.isBlank()) {
            throw BhashiniClientException.malformedResponse();
        }
        log.info(
                "translation_from_canonical requestId={} requestedLanguage={} detectedLanguage={} processingLanguage={} latencyMs={}",
                requestId,
                metadata.requestedLanguage(),
                metadata.detectedLanguage(),
                metadata.processingLanguage(),
                Duration.ofNanos(System.nanoTime() - started).toMillis()
        );
        return translated;
    }

    public List<String> toCanonicalList(List<String> values, Language sourceLanguage) {
        if (values == null || values.isEmpty() || sourceLanguage == CANONICAL_LANGUAGE) {
            return values == null ? List.of() : values;
        }
        return values.stream()
                .map(value -> bhashiniClient.translate(value, sourceLanguage, CANONICAL_LANGUAGE))
                .toList();
    }

    public List<String> fromCanonicalList(List<String> values, LanguageMetadata metadata, String requestId) {
        if (values == null || values.isEmpty() || metadata.requestedLanguage() == CANONICAL_LANGUAGE) {
            return values == null ? List.of() : values;
        }
        return values.stream()
                .map(value -> fromCanonical(value, metadata, requestId))
                .toList();
    }

    public Language detect(String text) {
        if (text == null || text.isBlank()) {
            return CANONICAL_LANGUAGE;
        }
        return text.codePoints().anyMatch(codePoint -> codePoint >= 0x0B80 && codePoint <= 0x0BFF)
                ? Language.TA
                : text.codePoints().anyMatch(codePoint -> codePoint >= 0x0900 && codePoint <= 0x097F)
                ? Language.HI
                : Language.EN;
    }
}
