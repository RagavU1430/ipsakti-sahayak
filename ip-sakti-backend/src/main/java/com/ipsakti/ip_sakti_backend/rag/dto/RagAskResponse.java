package com.ipsakti.ip_sakti_backend.rag.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record RagAskResponse(
        String answer,
        Double confidence,
        Boolean abstained,
        List<RagCitation> citations,
        List<RagSource> sources
) {

    @JsonIgnore
    public RagAnswerSource answerSource() {
        if (Boolean.TRUE.equals(abstained)) {
            return RagAnswerSource.ABSTAINED;
        }
        if ((citations != null && !citations.isEmpty()) || (sources != null && !sources.isEmpty())) {
            return RagAnswerSource.RAG_GROUNDED;
        }
        return RagAnswerSource.GENERAL_FALLBACK;
    }

    @JsonProperty("answer_source")
    public String answerSourceValue() {
        return answerSource().name().toLowerCase();
    }
}
