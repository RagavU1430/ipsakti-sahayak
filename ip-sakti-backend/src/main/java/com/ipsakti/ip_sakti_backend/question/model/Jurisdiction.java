package com.ipsakti.ip_sakti_backend.question.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum Jurisdiction {
    INDIA,
    INTERNATIONAL,
    AUTO;

    @JsonCreator
    public static Jurisdiction fromJson(String value) {
        if (value == null || value.isBlank()) {
            return AUTO;
        }
        return Jurisdiction.valueOf(value.trim().toUpperCase());
    }

    @JsonValue
    public String toJson() {
        return name();
    }
}
