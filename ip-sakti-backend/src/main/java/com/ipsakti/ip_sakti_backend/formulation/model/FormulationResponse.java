package com.ipsakti.ip_sakti_backend.formulation.model;

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
        List<QuestionSource> sources
) {
}
