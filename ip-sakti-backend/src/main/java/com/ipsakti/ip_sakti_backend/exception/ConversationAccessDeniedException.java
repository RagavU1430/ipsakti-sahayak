package com.ipsakti.ip_sakti_backend.exception;

import org.springframework.http.HttpStatus;

public class ConversationAccessDeniedException extends RuntimeException {

    private final HttpStatus status = HttpStatus.FORBIDDEN;
    private final String code = "FORBIDDEN";

    public ConversationAccessDeniedException(String message) {
        super(message);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
