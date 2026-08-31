package com.ipsakti.ip_sakti_backend.regulatory.model;

import com.ipsakti.ip_sakti_backend.question.model.Jurisdiction;
import java.util.List;

public record RegulatoryAnalysisResponse(
        Jurisdiction jurisdiction,
        RegulatoryStatus overallStatus,
        List<RegulatoryEngineResult> engines,
        Double overallConfidence,
        Boolean needsClarification,
        List<String> questions,
        String reason
) {
}
