package com.ipsakti.ip_sakti_backend.formulation.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ipsakti.ip_sakti_backend.question.model.Language;
import com.ipsakti.ip_sakti_backend.question.model.QuestionCitation;
import com.ipsakti.ip_sakti_backend.question.model.QuestionSource;
import java.util.List;

public record FormulationResponse(
        FormulationClassification classification,
        Double confidence,
        Boolean needsClarification,
        List<String> questions,
        String reason,
        FormulationStatus status,
        RegulatoryRoute regulatoryRoute,
        List<QuestionCitation> citations,
        List<QuestionSource> sources,
        Language language,
        @JsonProperty("detected_language") Language detectedLanguage,
        @JsonProperty("processing_language") Language processingLanguage
) {
    public FormulationResponse(
            FormulationClassification classification,
            Double confidence,
            Boolean needsClarification,
            List<String> questions,
            String reason,
            FormulationStatus status,
            RegulatoryRoute regulatoryRoute,
            List<QuestionCitation> citations,
            List<QuestionSource> sources
    ) {
        this(classification, confidence, needsClarification, questions, reason, status, regulatoryRoute,
                citations, sources, Language.EN, Language.EN, Language.EN);
    }
}
