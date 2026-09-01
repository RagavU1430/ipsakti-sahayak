package com.ipsakti.ip_sakti_backend.exception;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(RagClientException.class)
    public ResponseEntity<ApiErrorResponse> handleRagClientException(RagClientException ex) {
        return ResponseEntity
                .status(ex.getStatus())
                .body(ApiErrorResponse.of("RAG service error", ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(BhashiniClientException.class)
    public ResponseEntity<ApiErrorResponse> handleBhashiniClientException(BhashiniClientException ex) {
        return ResponseEntity
                .status(ex.getStatus())
                .body(ApiErrorResponse.of("Translation service error", ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ApiErrorResponse> handleBadRequest(Exception ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of("Invalid request", "INVALID_REQUEST", "Request validation failed."));
    }

    @ExceptionHandler(ConversationNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleConversationNotFound(ConversationNotFoundException ex) {
        return ResponseEntity
                .status(ex.getStatus())
                .body(ApiErrorResponse.of("Conversation not found", ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(ConversationAccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleConversationAccessDenied(ConversationAccessDeniedException ex) {
        return ResponseEntity
                .status(ex.getStatus())
                .body(ApiErrorResponse.of("Access denied", ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleSpringAccessDenied(org.springframework.security.access.AccessDeniedException ex) {
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiErrorResponse.of("Access denied", "FORBIDDEN", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex) {
        log.error("backend_internal_error", ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiErrorResponse.of("Internal backend error", "INTERNAL_ERROR", "The backend could not complete the request."));
    }
}
