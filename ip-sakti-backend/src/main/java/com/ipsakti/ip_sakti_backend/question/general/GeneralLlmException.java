package com.ipsakti.ip_sakti_backend.question.general;

public class GeneralLlmException extends RuntimeException {
    private final String code;

    public GeneralLlmException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
