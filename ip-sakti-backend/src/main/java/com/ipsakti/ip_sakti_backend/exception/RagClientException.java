package com.ipsakti.ip_sakti_backend.exception;

import org.springframework.http.HttpStatus;

public class RagClientException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    private RagClientException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static RagClientException timeout() {
        return new RagClientException(HttpStatus.GATEWAY_TIMEOUT, "RAG_TIMEOUT", "The RAG service timed out.");
    }

    public static RagClientException unavailable() {
        return new RagClientException(HttpStatus.SERVICE_UNAVAILABLE, "RAG_UNAVAILABLE", "The RAG service is unavailable.");
    }

    public static RagClientException malformedResponse() {
        return new RagClientException(HttpStatus.BAD_GATEWAY, "RAG_MALFORMED_RESPONSE", "The RAG service returned an invalid response.");
    }

    public static RagClientException unexpectedStatus(int statusCode) {
        return new RagClientException(HttpStatus.BAD_GATEWAY, "RAG_UNEXPECTED_STATUS", "The RAG service returned HTTP " + statusCode + ".");
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
