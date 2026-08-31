package com.ipsakti.ip_sakti_backend.question.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum Language {
    EN("en"),
    HI("hi"),
    TA("ta");

    private final String value;

    Language(String value) {
        this.value = value;
    }

    @JsonCreator
    public static Language fromJson(String value) {
        if (value == null || value.isBlank()) {
            return EN;
        }
        String normalized = value.trim().toLowerCase();
        for (Language language : values()) {
            if (language.value.equals(normalized)) {
                return language;
            }
        }
        throw new IllegalArgumentException("Unsupported language.");
    }

    @JsonValue
    public String toJson() {
        return value;
    }
}
