package com.ipsakti.ip_sakti_backend.rag;

import com.ipsakti.ip_sakti_backend.exception.RagClientException;
import com.ipsakti.ip_sakti_backend.rag.dto.RagAskRequest;
import com.ipsakti.ip_sakti_backend.rag.dto.RagAskResponse;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Service
public class RagClient {

    private static final Logger log = LoggerFactory.getLogger(RagClient.class);

    private final RestClient ragRestClient;

    public RagClient(@Qualifier("ragRestClient") RestClient ragRestClient) {
        this.ragRestClient = ragRestClient;
    }

    public RagAskResponse ask(RagAskRequest request) {
        long started = System.nanoTime();
        log.info("rag_request_initiated questionLength={}", request.question().length());
        try {
            RagAskResponse response = ragRestClient
                    .post()
                    .uri("/api/v1/ask")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(RagAskResponse.class);

            if (response == null || response.answer() == null || response.confidence() == null
                    || response.abstained() == null || response.citations() == null || response.sources() == null) {
                throw RagClientException.malformedResponse();
            }

            log.info(
                    "rag_response_received latencyMs={} classification={}",
                    Duration.ofNanos(System.nanoTime() - started).toMillis(),
                    response.answerSource()
            );
            return response;
        } catch (RestClientResponseException ex) {
            log.warn(
                    "rag_unexpected_http_status status={} latencyMs={}",
                    ex.getStatusCode().value(),
                    Duration.ofNanos(System.nanoTime() - started).toMillis()
            );
            throw RagClientException.unexpectedStatus(ex.getStatusCode().value());
        } catch (ResourceAccessException ex) {
            long latencyMs = Duration.ofNanos(System.nanoTime() - started).toMillis();
            if (isTimeout(ex)) {
                log.warn("rag_timeout latencyMs={}", latencyMs);
                throw RagClientException.timeout();
            }
            log.warn("rag_unavailable latencyMs={}", latencyMs);
            throw RagClientException.unavailable();
        } catch (RagClientException ex) {
            log.warn("rag_malformed_response latencyMs={}", Duration.ofNanos(System.nanoTime() - started).toMillis());
            throw ex;
        } catch (RestClientException ex) {
            log.warn("rag_malformed_response latencyMs={}", Duration.ofNanos(System.nanoTime() - started).toMillis());
            throw RagClientException.malformedResponse();
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
}
