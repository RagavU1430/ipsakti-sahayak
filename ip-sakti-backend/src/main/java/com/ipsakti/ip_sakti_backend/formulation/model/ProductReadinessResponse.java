package com.ipsakti.ip_sakti_backend.formulation.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ipsakti.ip_sakti_backend.question.model.Language;
import com.ipsakti.ip_sakti_backend.question.model.QuestionCitation;
import com.ipsakti.ip_sakti_backend.question.model.QuestionSource;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProductReadinessResponse(
        Map<String, Object> product,
        Map<String, Object> classification,
        Map<String, Object> regulatory,
        Map<String, Object> documents,
        List<IngredientVerificationItem> ingredients,
        List<ClaimAnalysisItem> claims,
        Map<String, Object> traditionalKnowledge,
        Map<String, Object> biodiversityAbs,
        Map<String, Object> ip,
        List<DocumentGapItem> gaps,
        List<String> nextSteps,
        List<QuestionCitation> citations,
        List<QuestionSource> sources,
        Double confidence,
        String status,
        Boolean abstained,
        ReadinessScores scores,
        String report,
        List<String> questions,
        Language language,
        @JsonProperty("detected_language") Language detectedLanguage,
        @JsonProperty("processing_language") Language processingLanguage
) {
}
