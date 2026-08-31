package com.ipsakti.ip_sakti_backend.formulation.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum FormulationStatus {
    CLASSIFIED("classified"),
    NEEDS_CLARIFICATION("needs_clarification"),
    INSUFFICIENT_EVIDENCE("insufficient_evidence");

    private final String value;

    FormulationStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
