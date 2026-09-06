package com.ipsakti.ip_sakti_backend.exception;

import jakarta.validation.ConstraintViolationException;
import com.ipsakti.ip_sakti_backend.question.general.GeneralLlmException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(RagClientException.class)
    public ResponseEntity<ApiErrorResponse> handleRagClientException(RagClientException ex) {
        return ResponseEntity
                .status(ex.getStatus())
                .body(ApiErrorResponse.of("RAG service error", ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(TranslationException.class)
    public ResponseEntity<ApiErrorResponse> handleTranslationException(TranslationException ex) {
        return ResponseEntity
                .status(ex.getStatus())
                .body(ApiErrorResponse.of("Translation service error", ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(GeneralLlmException.class)
    public ResponseEntity<ApiErrorResponse> handleGeneralLlmException(GeneralLlmException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiErrorResponse.of("General AI service error", ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Request validation failed.");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of("Invalid request", "INVALID_REQUEST", details));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        String details = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Request validation failed.");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of("Invalid request", "INVALID_REQUEST", details));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleNotReadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of("Invalid request", "INVALID_REQUEST", "Malformed JSON request."));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiErrorResponse.of("Recording too large", "AUDIO_TOO_LARGE",
                        "The recording exceeds the configured upload size limit."));
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrity(org.springframework.dao.DataIntegrityViolationException ex) {
        log.warn("data_integrity_violation", ex);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of("Conflict", "DATA_CONFLICT", "A data conflict occurred (possible duplicate)."));
    }

    @ExceptionHandler({org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class, org.springframework.web.bind.MissingServletRequestParameterException.class})
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(Exception ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of("Invalid request", "INVALID_REQUEST", ex.getMessage()));
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
