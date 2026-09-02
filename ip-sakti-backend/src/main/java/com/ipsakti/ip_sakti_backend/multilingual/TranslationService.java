package com.ipsakti.ip_sakti_backend.multilingual;

import com.ipsakti.ip_sakti_backend.exception.TranslationException;
import com.ipsakti.ip_sakti_backend.question.model.Language;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TranslationService {

    private static final Logger log = LoggerFactory.getLogger(TranslationService.class);
    private static final Language CANONICAL_LANGUAGE = Language.EN;

    private final TranslationProvider translationProvider;

    public TranslationService(TranslationProvider translationProvider) {
        this.translationProvider = translationProvider;
    }

    public TranslatedText toCanonical(String text, Language requestedLanguage, String requestId) {
        Language requested = requestedLanguage == null ? detect(text) : requestedLanguage;
        Language detected = detect(text);
        if (requested == CANONICAL_LANGUAGE) {
            return new TranslatedText(text, text, new LanguageMetadata(requested, detected, CANONICAL_LANGUAGE));
        }

        long started = System.nanoTime();
        String translated = translationProvider.translate(text, requested, CANONICAL_LANGUAGE);
        if (translated == null || translated.isBlank()) {
            throw TranslationException.malformedResponse();
        }
        log.info(
                "translation_to_canonical provider={} requestId={} requestedLanguage={} detectedLanguage={} processingLanguage={} latencyMs={}",
                translationProvider.providerName(),
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
        String translated = translationProvider.translate(text, CANONICAL_LANGUAGE, metadata.requestedLanguage());
        if (translated == null || translated.isBlank()) {
            throw TranslationException.malformedResponse();
        }
        log.info(
                "translation_from_canonical provider={} requestId={} requestedLanguage={} detectedLanguage={} processingLanguage={} latencyMs={}",
                translationProvider.providerName(),
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
                .map(value -> translationProvider.translate(value, sourceLanguage, CANONICAL_LANGUAGE))
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
        // Tamil 0B80-0BFF, Telugu 0C00-0C7F, Kannada 0C80-0CBF, Malayalam 0D00-0D7F, Devanagari 0900-097F (Hindi)
        if (text.codePoints().anyMatch(cp -> cp >= 0x0B80 && cp <= 0x0BFF)) return Language.TA;
        if (text.codePoints().anyMatch(cp -> cp >= 0x0C00 && cp <= 0x0C7F)) return Language.TE;
        if (text.codePoints().anyMatch(cp -> cp >= 0x0C80 && cp <= 0x0CBF)) return Language.KN;
        if (text.codePoints().anyMatch(cp -> cp >= 0x0D00 && cp <= 0x0D7F)) return Language.ML;
        if (text.codePoints().anyMatch(cp -> cp >= 0x0900 && cp <= 0x097F)) return Language.HI;
        return Language.EN;
    }
}
