package com.ipsakti.ip_sakti_backend.multilingual;

import com.ipsakti.ip_sakti_backend.config.GeminiProperties;
import com.ipsakti.ip_sakti_backend.exception.TranslationException;
import com.ipsakti.ip_sakti_backend.question.model.Language;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

public class GeminiTranslationProvider implements TranslationProvider {

    private static final Logger log = LoggerFactory.getLogger(GeminiTranslationProvider.class);

    static final String TRANSLATION_PROMPT_VERSION = "ip-sakti-legal-v4";

    private final RestClient restClient;
    private final GeminiProperties properties;
    private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();
    private final AtomicReference<String> preferredModel = new AtomicReference<>();
    private final ConcurrentHashMap<String, Long> rateLimitedUntil = new ConcurrentHashMap<>();

    public GeminiTranslationProvider(RestClient restClient, GeminiProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }
    @Override
    public boolean isConfigured() {
        return properties.configured();
    }

    @Override
    public String providerName() {
        return "gemini-" + String.join(",", properties.modelCandidates());
    }

    @Override
    public String translate(String text, Language sourceLanguage, Language targetLanguage) {
        if (sourceLanguage == targetLanguage) {
            return text;
        }
        if (!isConfigured()) {
            throw TranslationException.notConfigured();
        }
        if (text == null || text.isBlank()) {
            return text;
        }

        String cacheKey = cacheKey(sourceLanguage, targetLanguage, text);
        String cached = cache.get(cacheKey);
        if (cached != null) {
            log.debug("gemini_translation_cache cacheHit=true sourceLanguage={} targetLanguage={} cacheKeyHash={}",
                    sourceLanguage, targetLanguage, cacheKey.substring(cacheKey.length() - 16));
            return cached;
        }
        log.debug("gemini_translation_cache cacheHit=false sourceLanguage={} targetLanguage={} cacheKeyHash={}",
                sourceLanguage, targetLanguage, cacheKey.substring(cacheKey.length() - 16));

        String prompt = buildPrompt(text, sourceLanguage, targetLanguage);
        String translated = callGemini(prompt, sourceLanguage, targetLanguage);

        // Cache only successful translations, limited size
        if (cache.size() < 1000) {
            cache.put(cacheKey, translated);
        }
        return translated;
    }

    @Override
    public void invalidate(String text, Language sourceLanguage, Language targetLanguage) {
        if (text != null && sourceLanguage != null && targetLanguage != null) {
            cache.remove(cacheKey(sourceLanguage, targetLanguage, text));
        }
    }

    private String buildPrompt(String text, Language source, Language target) {
        String sourceName = languageDisplayName(source);
        String targetName = languageDisplayName(target);
        boolean toEnglish = target == Language.EN;
        boolean hasProtectedTokens = text.contains("[[IPSAKTI_TOKEN_");

        return "You are a faithful translation component for an evidence-grounded IP and regulatory assistant.\n\n"
                + "Translate the provided text from " + sourceName + " (" + source.toJson() + ") to "
                + targetName + " (" + target.toJson() + ").\n\n"
                + (toEnglish ? "Translation only; do not answer the user's question.\n" : "Translation only; do not independently answer or reinterpret the content.\n")
                + "Preserve meaning exactly. Do not add information, remove information, summarize, reinterpret, provide legal advice, change legal meaning, or convert uncertainty into certainty.\n"
                + "Do not change section numbers, citation identifiers, document identifiers, URLs, percentages, dates, numbers, Markdown syntax, bullet points, headings, or confidence values.\n"
                + (hasProtectedTokens
                        ? "The supplied text contains protected IPSAKTI_TOKEN placeholders. Copy every placeholder exactly once and do not translate, add, remove, duplicate, or modify placeholders. You may move a whole placeholder only where target-language grammar requires it.\n"
                        : "")
                + "Only translate natural-language explanatory content. If a legal term has no reliable equivalent, preserve canonical English instead of inventing one.\n\n"
                + "Return only the translated text.\n\nText to translate:\n" + text;
    }

    static String cacheKey(Language sourceLanguage, Language targetLanguage, String text) {
        return TRANSLATION_PROMPT_VERSION + ":" + sourceLanguage.toJson() + "->" + targetLanguage.toJson() + ":" + sha256(text);
    }

    private static String sha256(String text) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder value = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) value.append(String.format("%02x", b));
            return value.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private String languageDisplayName(Language lang) {
        return switch (lang) {
            case EN -> "English";
            case HI -> "Hindi";
            case TA -> "Tamil";
            case TE -> "Telugu";
            case KN -> "Kannada";
            case ML -> "Malayalam";
        };
    }

    private String callGemini(String prompt, Language source, Language target) {
        List<String> rawCandidates = properties.modelCandidates();
        if (rawCandidates.isEmpty()) {
            throw TranslationException.notConfigured();
        }

        long now = System.currentTimeMillis();
        List<String> candidates = new ArrayList<>();
        String preferred = preferredModel.get();
        if (preferred != null && rawCandidates.contains(preferred) && now >= rateLimitedUntil.getOrDefault(preferred, 0L)) {
            candidates.add(preferred);
        }
        for (String m : rawCandidates) {
            if (!candidates.contains(m) && now >= rateLimitedUntil.getOrDefault(m, 0L)) {
                candidates.add(m);
            }
        }
        if (candidates.isEmpty()) {
            candidates = rawCandidates;
        }

        TranslationException lastFailure = null;
        for (String model : candidates) {
            try {
                String result = callGeminiModel(prompt, source, target, model, candidates.size() > 1);
                preferredModel.set(model);
                rateLimitedUntil.remove(model);
                return result;
            } catch (TranslationException ex) {
                lastFailure = ex;
                if (!shouldTryNextModel(ex)) {
                    throw ex;
                }
                log.warn("gemini_model_fallback model={} sourceLanguage={} targetLanguage={} reason={}",
                        model, source, target, ex.getCode());
            }
        }
        throw lastFailure == null ? TranslationException.translationUnavailable() : lastFailure;
    }

    private String callGeminiModel(String prompt, Language source, Language target, String model, boolean hasAlternativeModels) {
        long started = System.nanoTime();
        int maxRetries = 1; // limited retry for transient failures only
        int attempt = 0;
        while (true) {
            attempt++;
            try {
                GeminiRequest request = new GeminiRequest(
                        List.of(new Content(List.of(new Part(prompt)))),
                        new GenerationConfig(0.0, 4000)
                );

                String path = "/v1beta/models/" + model + ":generateContent?key=" + properties.getApiKey();

                GeminiResponse response = restClient.post()
                        .uri(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .body(request)
                        .retrieve()
                        .body(GeminiResponse.class);

                if (response == null || response.candidates() == null || response.candidates().isEmpty()) {
                    throw TranslationException.malformedResponse();
                }
                Content content = response.candidates().get(0).content();
                if (content == null || content.parts() == null || content.parts().isEmpty()) {
                    throw TranslationException.malformedResponse();
                }
                String translated = content.parts().get(0).text();
                if (translated == null || translated.isBlank()) {
                    throw TranslationException.malformedResponse();
                }
                translated = translated.trim();
                long latency = Duration.ofNanos(System.nanoTime() - started).toMillis();
                log.info("gemini_translation_success sourceLanguage={} targetLanguage={} model={} latencyMs={} attempt={}",
                        source, target, model, latency, attempt);
                return translated;

            } catch (RestClientResponseException ex) {
                int status = ex.getStatusCode().value();
                long latency = Duration.ofNanos(System.nanoTime() - started).toMillis();
                // Invalid auth/config must fail closed. Missing model can safely fall through to the next configured candidate.
                if (status == 401 || status == 403) {
                    log.warn("gemini_auth_or_permission_error status={} sourceLanguage={} targetLanguage={} model={} latencyMs={}",
                            status, source, target, model, latency);
                    throw TranslationException.notConfigured();
                }
                if (status == 400) {
                    log.warn("gemini_bad_request status={} sourceLanguage={} targetLanguage={} model={} latencyMs={}",
                            status, source, target, model, latency);
                    throw TranslationException.unexpectedStatus(status);
                }
                if (status == 404) {
                    log.warn("gemini_model_not_found status={} sourceLanguage={} targetLanguage={} model={} latencyMs={}",
                            status, source, target, model, latency);
                    throw TranslationException.modelUnavailable(model);
                }
                if (status == 429) {
                    rateLimitedUntil.put(model, System.currentTimeMillis() + 60_000L);
                    log.warn("gemini_rate_limited sourceLanguage={} targetLanguage={} model={} latencyMs={}",
                            source, target, model, latency);
                    if (hasAlternativeModels) {
                        throw TranslationException.unexpectedStatus(status);
                    }
                    if (attempt <= maxRetries) {
                        try { Thread.sleep(500); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                        continue;
                    }
                }
                log.warn("gemini_unexpected_http_status status={} sourceLanguage={} targetLanguage={} model={} latencyMs={}",
                        status, source, target, model, latency);
                if (attempt <= maxRetries && (status == 500 || status == 502 || status == 503)) {
                    try { Thread.sleep(500); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                    continue;
                }
                throw TranslationException.unexpectedStatus(status);
            } catch (ResourceAccessException ex) {
                long latency = Duration.ofNanos(System.nanoTime() - started).toMillis();
                if (isTimeout(ex) && attempt <= maxRetries) {
                    log.warn("gemini_timeout sourceLanguage={} targetLanguage={} model={} latencyMs={} retrying",
                            source, target, model, latency);
                    try { Thread.sleep(500); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                    continue;
                }
                if (isTimeout(ex)) {
                    log.warn("gemini_timeout sourceLanguage={} targetLanguage={} model={} latencyMs={}",
                            source, target, model, latency);
                    throw TranslationException.timeout();
                }
                log.warn("gemini_unavailable sourceLanguage={} targetLanguage={} model={} latencyMs={}",
                        source, target, model, latency);
                if (attempt <= maxRetries) {
                    try { Thread.sleep(500); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                    continue;
                }
                throw TranslationException.unavailable();
            } catch (TranslationException ex) {
                throw ex;
            } catch (RestClientException ex) {
                log.warn("gemini_malformed_response sourceLanguage={} targetLanguage={} model={} error={}",
                        source, target, model, ex.getMessage());
                throw TranslationException.malformedResponse();
            }
        }
    }

    private boolean shouldTryNextModel(TranslationException ex) {
        return "GEMINI_MODEL_UNAVAILABLE".equals(ex.getCode())
                || "TRANSLATION_UNEXPECTED_STATUS".equals(ex.getCode())
                || "TRANSLATION_TIMEOUT".equals(ex.getCode())
                || "TRANSLATION_UNAVAILABLE".equals(ex.getCode())
                || "TRANSLATION_MALFORMED_RESPONSE".equals(ex.getCode());
    }

    private boolean isTimeout(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SocketTimeoutException) {
                return true;
            }
            if (current instanceof IOException && current.getMessage() != null
                    && current.getMessage().toLowerCase().contains("timed out")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    // Gemini DTOs
    private record GeminiRequest(List<Content> contents, GenerationConfig generationConfig) {}
    private record Content(List<Part> parts) {}
    private record Part(String text) {}
    private record GenerationConfig(double temperature, int maxOutputTokens) {}
    private record GeminiResponse(List<Candidate> candidates) {}
    private record Candidate(Content content) {}
}
