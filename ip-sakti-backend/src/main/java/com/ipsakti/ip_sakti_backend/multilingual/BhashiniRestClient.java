package com.ipsakti.ip_sakti_backend.multilingual;

import com.ipsakti.ip_sakti_backend.config.BhashiniProperties;
import com.ipsakti.ip_sakti_backend.exception.BhashiniClientException;
import com.ipsakti.ip_sakti_backend.question.model.Language;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

class BhashiniRestClient implements BhashiniClient {

    private static final Logger log = LoggerFactory.getLogger(BhashiniRestClient.class);

    private final RestClient restClient;
    private final BhashiniProperties properties;

    BhashiniRestClient(RestClient restClient, BhashiniProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public String translate(String text, Language sourceLanguage, Language targetLanguage) {
        if (sourceLanguage == targetLanguage) {
            return text;
        }
        if (!properties.configured()) {
            throw BhashiniClientException.notConfigured();
        }

        long started = System.nanoTime();
        try {
            BhashiniTranslationResponse response = restClient
                    .post()
                    .uri("/translate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .body(new BhashiniTranslationRequest(
                            text,
                            sourceLanguage.toJson(),
                            targetLanguage.toJson(),
                            properties.getUserId(),
                            properties.getTranslationServiceId(),
                            properties.getPipelineId()
                    ))
                    .retrieve()
                    .body(BhashiniTranslationResponse.class);

            if (response == null || response.translatedText() == null || response.translatedText().isBlank()) {
                throw BhashiniClientException.malformedResponse();
            }

            log.info(
                    "bhashini_translation_success sourceLanguage={} targetLanguage={} latencyMs={}",
                    sourceLanguage,
                    targetLanguage,
                    Duration.ofNanos(System.nanoTime() - started).toMillis()
            );
            return response.translatedText();
        } catch (RestClientResponseException ex) {
            log.warn(
                    "bhashini_unexpected_http_status status={} sourceLanguage={} targetLanguage={} latencyMs={}",
                    ex.getStatusCode().value(),
                    sourceLanguage,
                    targetLanguage,
                    Duration.ofNanos(System.nanoTime() - started).toMillis()
            );
            throw BhashiniClientException.unexpectedStatus(ex.getStatusCode().value());
        } catch (ResourceAccessException ex) {
            long latencyMs = Duration.ofNanos(System.nanoTime() - started).toMillis();
            if (isTimeout(ex)) {
                log.warn("bhashini_timeout sourceLanguage={} targetLanguage={} latencyMs={}",
                        sourceLanguage, targetLanguage, latencyMs);
                throw BhashiniClientException.timeout();
            }
            log.warn("bhashini_unavailable sourceLanguage={} targetLanguage={} latencyMs={}",
                    sourceLanguage, targetLanguage, latencyMs);
            throw BhashiniClientException.unavailable();
        } catch (BhashiniClientException ex) {
            throw ex;
        } catch (RestClientException ex) {
            log.warn("bhashini_malformed_response sourceLanguage={} targetLanguage={} latencyMs={}",
                    sourceLanguage, targetLanguage, Duration.ofNanos(System.nanoTime() - started).toMillis());
            throw BhashiniClientException.malformedResponse();
        }
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

    private record BhashiniTranslationRequest(
            String text,
            String sourceLanguage,
            String targetLanguage,
            String userId,
            String translationServiceId,
            String pipelineId
    ) {
    }

    private record BhashiniTranslationResponse(String translatedText) {
    }
}
