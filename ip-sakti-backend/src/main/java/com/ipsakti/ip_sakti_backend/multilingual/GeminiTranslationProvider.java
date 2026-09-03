package com.ipsakti.ip_sakti_backend.multilingual;

import com.ipsakti.ip_sakti_backend.config.GeminiProperties;
import com.ipsakti.ip_sakti_backend.exception.TranslationException;
import com.ipsakti.ip_sakti_backend.question.model.Language;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

public class GeminiTranslationProvider implements TranslationProvider {

    private static final Logger log = LoggerFactory.getLogger(GeminiTranslationProvider.class);

    // Legal identifier protection - placeholders for critical terms
    private static final Pattern LEGAL_PATTERN = Pattern.compile(
            "\\b(Section\\s+3\\(p\\)|Section\\s+3\\(e\\)|Section\\s+\\d+[A-Za-z]?|Rule\\s+\\d+|Article\\s+\\d+|Regulation\\s+\\d+|Patents Act,?\\s+1970|Trade Marks Act,?\\s+1999|Geographical Indications|Traditional Knowledge|GRATK|TKDL|WIPO|WTO|ABS|GI|PCT)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private final RestClient restClient;
    private final GeminiProperties properties;
    private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

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

        // Simple cache for translation results (safe for stateless short texts)
        String cacheKey = sourceLanguage.toJson() + "->" + targetLanguage.toJson() + ":" + text.hashCode() + ":" + text.length();
        String cached = cache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        // Placeholder protection for legal identifiers
        Map<String, String> placeholders = new ConcurrentHashMap<>();
        String protectedText = protectLegalIdentifiers(text, placeholders);

        String prompt = buildPrompt(protectedText, sourceLanguage, targetLanguage);
        String translated = callGemini(prompt, sourceLanguage, targetLanguage);
        String restored = restoreLegalIdentifiers(translated, placeholders);

        // Cache only successful translations, limited size
        if (cache.size() < 1000) {
            cache.put(cacheKey, restored);
        }
        return restored;
    }

    private String protectLegalIdentifiers(String text, Map<String, String> placeholders) {
        Matcher m = LEGAL_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();
        int idx = 0;
        while (m.find()) {
            String original = m.group();
            String placeholder = "__LEGAL_REF_" + idx + "__";
            placeholders.put(placeholder, original);
            m.appendReplacement(sb, Matcher.quoteReplacement(placeholder));
            idx++;
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private String restoreLegalIdentifiers(String text, Map<String, String> placeholders) {
        String result = text;
        for (Map.Entry<String, String> e : placeholders.entrySet()) {
            result = result.replace(e.getKey(), e.getValue());
        }
        return result;
    }

    private String buildPrompt(String text, Language source, Language target) {
        String sourceName = languageDisplayName(source);
        String targetName = languageDisplayName(target);
        boolean toEnglish = target == Language.EN;

        if (toEnglish) {
            return "You are a translation component for an Indian intellectual property knowledge system.\n\n"
                    + "Translate the user's text from " + sourceName + " (" + source.toJson() + ") into English.\n\n"
                    + "Translation only.\n"
                    + "Do not answer the question.\n"
                    + "Do not summarize.\n"
                    + "Do not explain.\n"
                    + "Do not add information.\n"
                    + "Preserve:\n"
                    + "- legal terminology\n"
                    + "- Act names\n"
                    + "- Rule names\n"
                    + "- Section numbers\n"
                    + "- subsection identifiers\n"
                    + "- patent numbers\n"
                    + "- application numbers\n"
                    + "- dates\n"
                    + "- numbers\n"
                    + "- URLs\n"
                    + "- quoted terms\n"
                    + "- product names\n"
                    + "- organization names\n"
                    + "- placeholders like __LEGAL_REF_N__ exactly\n\n"
                    + "Return only the translated text.\n\n"
                    + "Text to translate:\n" + text;
        } else {
            return "You are a translation component for an Indian intellectual property knowledge system.\n\n"
                    + "Translate the supplied authoritative answer from English into " + targetName + " (" + target.toJson() + ").\n\n"
                    + "Translation only.\n"
                    + "Do not add facts.\n"
                    + "Do not remove facts.\n"
                    + "Do not change legal meaning.\n"
                    + "Do not change uncertainty.\n"
                    + "Do not introduce legal advice.\n"
                    + "Preserve:\n"
                    + "- citations\n"
                    + "- document names\n"
                    + "- section references\n"
                    + "- rule references\n"
                    + "- numbers\n"
                    + "- dates\n"
                    + "- URLs\n"
                    + "- organization names\n"
                    + "- patent/trademark identifiers\n"
                    + "- bullet structure\n"
                    + "- headings\n"
                    + "- placeholders like __LEGAL_REF_N__ exactly\n\n"
                    + "The answer was generated from authoritative evidence.\n"
                    + "Do not independently answer or reinterpret the question.\n\n"
                    + "Return only the translated answer.\n\n"
                    + "Text to translate:\n" + text;
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
        List<String> candidates = properties.modelCandidates();
        if (candidates.isEmpty()) {
            throw TranslationException.notConfigured();
        }
        TranslationException lastFailure = null;
        for (String model : candidates) {
            try {
                return callGeminiModel(prompt, source, target, model);
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

    private String callGeminiModel(String prompt, Language source, Language target, String model) {
        long started = System.nanoTime();
        int maxRetries = 1; // limited retry for transient failures only
        int attempt = 0;
        while (true) {
            attempt++;
            try {
                GeminiRequest request = new GeminiRequest(
                        List.of(new Content(List.of(new Part(prompt)))),
                        new GenerationConfig(0.1, 4000)
                );

                // Gemini uses query param key, not header. Configure RestClient with baseUrl, do POST to /v1beta/models/{model}:generateContent?key=API
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
                if (status == 429 && attempt <= maxRetries) {
                    log.warn("gemini_rate_limited sourceLanguage={} targetLanguage={} model={} latencyMs={} retrying",
                            source, target, model, latency);
                    try { Thread.sleep(500); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                    continue;
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
