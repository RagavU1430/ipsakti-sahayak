package com.ipsakti.ip_sakti_backend.question.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record QuestionRequest(
        @NotBlank @Size(max = 4000) String question,
        Jurisdiction jurisdiction,
        Language language
) {
    public QuestionRequest {
        if (question != null) {
            question = String.join(" ", question.trim().split("\\s+"));
        }
        if (jurisdiction == null) {
            jurisdiction = Jurisdiction.AUTO;
        }
        if (language == null) {
            language = Language.EN;
        }
    }
}
