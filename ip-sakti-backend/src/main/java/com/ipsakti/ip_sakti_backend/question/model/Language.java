package com.ipsakti.ip_sakti_backend.question.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum Language {
    EN("en", "English", "English"),
    HI("hi", "Hindi", "हिन्दी"),
    TA("ta", "Tamil", "தமிழ்"),
    TE("te", "Telugu", "తెలుగు"),
    KN("kn", "Kannada", "ಕನ್ನಡ"),
    ML("ml", "Malayalam", "മലയാളം");

    private final String value;
    private final String displayName;
    private final String nativeName;

    Language(String value, String displayName, String nativeName) {
        this.value = value;
        this.displayName = displayName;
        this.nativeName = nativeName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getNativeName() {
        return nativeName;
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
