package com.ipsakti.ip_sakti_backend.exception;

import org.springframework.http.HttpStatus;

public class TranslationException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    private TranslationException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static TranslationException notConfigured() {
        return new TranslationException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "TRANSLATION_UNAVAILABLE",
                "Translation service is not configured for non-English requests."
        );
    }

    public static TranslationException translationUnavailable() {
        return new TranslationException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "TRANSLATION_UNAVAILABLE",
                "Translation service is temporarily unavailable."
        );
    }

    public static TranslationException unsupportedLanguage() {
        return new TranslationException(
                HttpStatus.BAD_REQUEST,
                "UNSUPPORTED_LANGUAGE",
                "The requested language is not supported."
        );
    }

    public static TranslationException timeout() {
        return new TranslationException(
                HttpStatus.GATEWAY_TIMEOUT,
                "TRANSLATION_TIMEOUT",
                "The translation service timed out."
        );
    }

    public static TranslationException unavailable() {
        return new TranslationException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "TRANSLATION_UNAVAILABLE",
                "The translation service is unavailable."
        );
    }

    public static TranslationException malformedResponse() {
        return new TranslationException(
                HttpStatus.BAD_GATEWAY,
                "TRANSLATION_MALFORMED_RESPONSE",
                "The translation service returned an invalid response."
        );
    }

    public static TranslationException unexpectedStatus(int statusCode) {
        return new TranslationException(
                HttpStatus.BAD_GATEWAY,
                "TRANSLATION_UNEXPECTED_STATUS",
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
