package com.ipsakti.ip_sakti_backend.regulatory.model;

import com.ipsakti.ip_sakti_backend.question.model.QuestionCitation;
import com.ipsakti.ip_sakti_backend.question.model.QuestionSource;
import java.util.List;

public record RegulatoryEngineResult(
        RegulatoryEngine engine,
        RegulatoryStatus status,
        Double confidence,
        String reason,
        List<String> considerations,
        GratkResourceType resourceType,
        List<QuestionCitation> citations,
        List<QuestionSource> sources
) {
}
