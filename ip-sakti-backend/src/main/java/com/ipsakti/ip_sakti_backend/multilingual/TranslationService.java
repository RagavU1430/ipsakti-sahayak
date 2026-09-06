package com.ipsakti.ip_sakti_backend.multilingual;

import com.ipsakti.ip_sakti_backend.exception.TranslationException;
import com.ipsakti.ip_sakti_backend.question.model.Language;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
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
        String translated = translate(text, requested, CANONICAL_LANGUAGE, requestId, "query");
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
        String translated = translate(text, CANONICAL_LANGUAGE, metadata.requestedLanguage(), requestId, "answer");
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
                .map(value -> translate(value, sourceLanguage, CANONICAL_LANGUAGE, "list", "query-list"))
                .toList();
    }

    public List<String> fromCanonicalList(List<String> values, LanguageMetadata metadata, String requestId) {
        if (values == null || values.isEmpty() || metadata.requestedLanguage() == CANONICAL_LANGUAGE) {
            return values == null ? List.of() : values;
        }
        if (values.size() == 1) {
            return List.of(fromCanonical(values.get(0), metadata, requestId));
        }
        // Attempt batch translation in a single network request to minimize latency
        try {
            String delimiter = "\n\n===IPSAKTI_SPLIT===\n\n";
            String joined = String.join(delimiter, values);
            String translated = fromCanonical(joined, metadata, requestId);
            String[] parts = translated.split("(?m)^\\s*===IPSAKTI_SPLIT===\\s*$");
            if (parts.length == values.size()) {
                List<String> result = new ArrayList<>(values.size());
                for (String part : parts) {
                    result.add(part.trim());
                }
                return result;
            }
            log.info("fromCanonicalList_batch_size_mismatch expected={} got={}, falling back to sequential",
                    values.size(), parts.length);
        } catch (Exception ex) {
            log.warn("fromCanonicalList_batch_failed, falling back to sequential: {}", ex.getMessage());
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

    private String translate(String text, Language source, Language target, String requestId, String stage) {
        if (TranslationProtection.containsReservedToken(text)) {
            throw TranslationException.malformedResponse();
        }
        TranslationProtection.ProtectedText protectedText = TranslationProtection.protect(text);
        for (int attempt = 1; attempt <= 2; attempt++) {
            String translated = translationProvider.translate(protectedText.text(), source, target);
            if (translated == null || translated.isBlank()) {
                throw TranslationException.malformedResponse();
            }
            try {
                String restored = TranslationProtection.restore(translated, protectedText);
                log.debug(
                        "translation_trace requestId={} stage={} sourceLanguage={} targetLanguage={} provider={} inputSha256={} inputLength={} protectedTokenCount={} outputLength={} integrityAttempt={} restorationSuccess=true validationSuccess=true",
                        requestId, stage, source, target, translationProvider.providerName(), digest(text), text.length(),
                        protectedText.tokens().size(), restored.length(), attempt);
                return restored;
            } catch (TranslationException integrityFailure) {
                log.warn(
                        "translation_integrity_failure requestId={} stage={} sourceLanguage={} targetLanguage={} provider={} inputSha256={} protectedTokenCount={} integrityAttempt={} retrying={}",
                        requestId, stage, source, target, translationProvider.providerName(), digest(text),
                        protectedText.tokens().size(), attempt, attempt == 1);
                if (attempt == 2) {
                    throw TranslationException.translationUnavailable();
                }
                translationProvider.invalidate(protectedText.text(), source, target);
            }
        }
        throw TranslationException.translationUnavailable();
    }

    private String digest(String text) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(16);
            for (int index = 0; index < 8; index++) {
                result.append(String.format("%02x", bytes[index]));
            }
            return result.toString();
        } catch (Exception ex) {
            return "unavailable";
        }
    }
}
