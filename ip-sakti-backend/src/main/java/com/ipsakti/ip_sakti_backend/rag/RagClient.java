package com.ipsakti.ip_sakti_backend.rag;

import com.ipsakti.ip_sakti_backend.config.RagProperties;
import com.ipsakti.ip_sakti_backend.exception.RagClientException;
import com.ipsakti.ip_sakti_backend.rag.dto.RagAskRequest;
import com.ipsakti.ip_sakti_backend.rag.dto.RagAskResponse;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import com.ipsakti.ip_sakti_backend.config.RequestTiming;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Service
public class RagClient {

    private static final Logger log = LoggerFactory.getLogger(RagClient.class);

    private final RestClient ragRestClient;
    private final RagProperties properties;

    public RagClient(@Qualifier("ragRestClient") RestClient ragRestClient) {
        this(ragRestClient, new RagProperties());
    }

    @Autowired
    public RagClient(@Qualifier("ragRestClient") RestClient ragRestClient, RagProperties properties) {
        this.ragRestClient = ragRestClient;
        this.properties = properties;
    }

    public RagAskResponse ask(RagAskRequest request) {
        long started = System.nanoTime();
        String requestId = MDC.get("request_id");
        if (requestId == null) requestId = java.util.UUID.randomUUID().toString();
        RagAskRequest outboundRequest = withDefaultTopK(request);
        log.info("rag_request_initiated questionLength={} topK={}", outboundRequest.question().length(), outboundRequest.topK());
        try {
            ResponseEntity<RagAskResponse> entity = ragRestClient
                    .post()
                    .uri("/api/v1/ask")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .header("X-Request-ID", requestId)
                    .body(outboundRequest)
                    .retrieve()
                    .toEntity(RagAskResponse.class);
            RagAskResponse response = entity.getBody();

            // These values originate in the RAG service's measured stages, not estimates.
            copyMetric(entity, "X-RAG-retrieval-ms", "retrieval");
            copyMetric(entity, "X-RAG-generation-ms", "llm");
            copyMetric(entity, "X-RAG-reranking-ms", "evidence_validation");
            String chunkCount = entity.getHeaders().getFirst("X-RAG-evidence-count");
            if (chunkCount == null) chunkCount = entity.getHeaders().getFirst("X-RAG-context-chunks");
            if (chunkCount != null) try { RequestTiming.chunks(Integer.parseInt(chunkCount)); } catch (NumberFormatException ignored) { }
            String generator = entity.getHeaders().getFirst("X-RAG-generator");
            RequestTiming.provider(generator == null ? "rag" : generator);

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

    public boolean checkHealth() {
        try {
            var response = ragRestClient.get()
                    .uri("/health")
                    .retrieve()
                    .toBodilessEntity();
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            return false;
        }
    }

    private RagAskRequest withDefaultTopK(RagAskRequest request) {
        if (request.topK() != null) {
            return request;
        }
        return new RagAskRequest(
                request.question(),
                request.domain(),
                request.jurisdiction(),
                properties.getDefaultTopK()
        );
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

    private void copyMetric(ResponseEntity<?> entity, String header, String stage) {
        String value = entity.getHeaders().getFirst(header);
        if (value == null) return;
        try { RequestTiming.set(stage, Double.parseDouble(value)); } catch (NumberFormatException ignored) { }
    }
}
