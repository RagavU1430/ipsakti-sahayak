package com.ipsakti.ip_sakti_backend.exception;

import org.springframework.http.HttpStatus;

public class ConversationNotFoundException extends RuntimeException {

    private final HttpStatus status = HttpStatus.NOT_FOUND;
    private final String code = "CONVERSATION_NOT_FOUND";

    public ConversationNotFoundException(String message) {
        super(message);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
