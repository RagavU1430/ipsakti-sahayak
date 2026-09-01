package com.ipsakti.ip_sakti_backend.regulatory.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ipsakti.ip_sakti_backend.question.model.Jurisdiction;
import com.ipsakti.ip_sakti_backend.question.model.Language;
import java.util.List;

public record RegulatoryAnalysisResponse(
        Jurisdiction jurisdiction,
        RegulatoryStatus overallStatus,
        List<RegulatoryEngineResult> engines,
        Double overallConfidence,
        Boolean needsClarification,
        List<String> questions,
        String reason,
        Language language,
        @JsonProperty("detected_language") Language detectedLanguage,
        @JsonProperty("processing_language") Language processingLanguage
) {
    public RegulatoryAnalysisResponse(
            Jurisdiction jurisdiction,
            RegulatoryStatus overallStatus,
            List<RegulatoryEngineResult> engines,
            Double overallConfidence,
            Boolean needsClarification,
            List<String> questions,
            String reason
    ) {
        this(jurisdiction, overallStatus, engines, overallConfidence, needsClarification, questions, reason,
                Language.EN, Language.EN, Language.EN);
    }
}
