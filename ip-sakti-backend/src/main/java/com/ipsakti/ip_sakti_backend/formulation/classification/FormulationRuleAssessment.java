package com.ipsakti.ip_sakti_backend.formulation.classification;

import com.ipsakti.ip_sakti_backend.formulation.model.FormulationClassification;
import java.util.List;
import java.util.Map;

public record FormulationRuleAssessment(
        Map<FormulationClassification, Integer> scores,
        FormulationClassification leadingClassification,
        int leadingScore,
        int secondScore,
        List<String> conflicts,
        List<String> missingInformation
) {
    public boolean hasConflict() {
        return !conflicts.isEmpty();
    }
}
