package com.ipsakti.ip_sakti_backend.question.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum AnswerType {
    RAG_GROUNDED("rag_grounded"),
    GENERAL_FALLBACK("general_fallback"),
    ABSTAINED("abstained");

    private final String value;

    AnswerType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
