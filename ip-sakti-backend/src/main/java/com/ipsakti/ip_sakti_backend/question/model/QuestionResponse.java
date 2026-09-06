package com.ipsakti.ip_sakti_backend.question.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import com.ipsakti.ip_sakti_backend.question.routing.QueryDomain;
import com.ipsakti.ip_sakti_backend.question.routing.RoutingReason;

public record QuestionResponse(
        String answer,
        AnswerType answerType,
        String route,
        QueryDomain domain,
        @JsonProperty("routing_reason") RoutingReason routingReason,
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
            Language detectedLanguage,
            Language processingLanguage,
            QuestionIntent intent,
            List<QuestionCitation> citations,
            List<QuestionSource> sources
    ) {
        this(answer, answerType, routeFor(answerType), null, null, confidence, abstained, jurisdiction, language,
                detectedLanguage, processingLanguage, intent, citations, sources);
    }

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
        this(answer, answerType, routeFor(answerType), null, null, confidence, abstained, jurisdiction, language,
                language, Language.EN, intent, citations, sources);
    }

    private static String routeFor(AnswerType answerType) {
        if (answerType == AnswerType.GENERAL_FALLBACK) return "GENERAL";
        return "RAG";
    }
}
