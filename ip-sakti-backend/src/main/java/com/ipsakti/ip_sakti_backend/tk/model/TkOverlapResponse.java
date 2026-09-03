package com.ipsakti.ip_sakti_backend.tk.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ipsakti.ip_sakti_backend.question.model.Language;
import com.ipsakti.ip_sakti_backend.question.model.QuestionCitation;
import com.ipsakti.ip_sakti_backend.question.model.QuestionSource;
import java.util.List;

public record TkOverlapResponse(
        TkOverlapClassification classification,
        Double confidence,
        @JsonProperty("overlap_types") List<TkOverlapType> overlapTypes,
        String explanation,
        List<TkEvidenceItem> evidence,
        String recommendation,
        List<QuestionCitation> citations,
        List<QuestionSource> sources,
        Boolean abstained,
        Language language,
        @JsonProperty("detected_language") Language detectedLanguage,
        @JsonProperty("processing_language") Language processingLanguage
) {
}
