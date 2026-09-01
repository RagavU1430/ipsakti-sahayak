package com.ipsakti.ip_sakti_backend.exception;

import org.springframework.http.HttpStatus;

public class BhashiniClientException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    private BhashiniClientException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static BhashiniClientException notConfigured() {
        return new BhashiniClientException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "BHASHINI_NOT_CONFIGURED",
                "Bhashini translation is not configured for non-English requests."
        );
    }

    public static BhashiniClientException unsupportedLanguage() {
        return new BhashiniClientException(
                HttpStatus.BAD_REQUEST,
                "UNSUPPORTED_LANGUAGE",
                "The requested language is not supported."
        );
    }

    public static BhashiniClientException timeout() {
        return new BhashiniClientException(
                HttpStatus.GATEWAY_TIMEOUT,
                "BHASHINI_TIMEOUT",
                "The translation service timed out."
        );
    }

    public static BhashiniClientException unavailable() {
        return new BhashiniClientException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "BHASHINI_UNAVAILABLE",
                "The translation service is unavailable."
        );
    }

    public static BhashiniClientException malformedResponse() {
        return new BhashiniClientException(
                HttpStatus.BAD_GATEWAY,
                "BHASHINI_MALFORMED_RESPONSE",
                "The translation service returned an invalid response."
        );
    }

    public static BhashiniClientException unexpectedStatus(int statusCode) {
        return new BhashiniClientException(
                HttpStatus.BAD_GATEWAY,
                "BHASHINI_UNEXPECTED_STATUS",
                "The translation service returned HTTP " + statusCode + "."
        );
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
