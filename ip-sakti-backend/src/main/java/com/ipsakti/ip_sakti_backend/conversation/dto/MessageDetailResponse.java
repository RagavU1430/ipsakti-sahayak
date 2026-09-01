package com.ipsakti.ip_sakti_backend.conversation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ipsakti.ip_sakti_backend.question.model.QuestionCitation;
import com.ipsakti.ip_sakti_backend.question.model.QuestionSource;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MessageDetailResponse(
        UUID id,
        String role,
        String content,
        @JsonProperty("response_type") String responseType,
        Double confidence,
        Boolean abstained,
        String jurisdiction,
        String language,
        @JsonProperty("detected_language") String detectedLanguage,
        @JsonProperty("processing_language") String processingLanguage,
        String intent,
        List<QuestionCitation> citations,
        List<QuestionSource> sources,
        @JsonProperty("created_at") Instant createdAt
) {
}
