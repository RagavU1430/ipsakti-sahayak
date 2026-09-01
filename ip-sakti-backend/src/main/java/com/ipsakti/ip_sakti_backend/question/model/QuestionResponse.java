package com.ipsakti.ip_sakti_backend.question.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record QuestionResponse(
        String answer,
        AnswerType answerType,
        Double confidence,
        Boolean abstained,
        Jurisdiction jurisdiction,
        Language language,
        @JsonProperty("detected_language") Language detectedLanguage,
        @JsonProperty("processing_language") Language processingLanguage,
        QuestionIntent intent,
        List<QuestionCitation> citations,
        List<QuestionSource> sources
) {
    public QuestionResponse(
            String answer,
            AnswerType answerType,
            Double confidence,
            Boolean abstained,
            Jurisdiction jurisdiction,
            Language language,
            QuestionIntent intent,
            List<QuestionCitation> citations,
            List<QuestionSource> sources
    ) {
        this(answer, answerType, confidence, abstained, jurisdiction, language, language, Language.EN, intent, citations, sources);
    }
}
