package com.ipsakti.ip_sakti_backend.formulation.classification;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class FormulationClarificationService {

    public List<String> questionsFor(FormulationRuleAssessment assessment) {
        List<String> questions = new ArrayList<>();

        if (assessment.missingInformation().contains("primary intended use or claims")) {
            questions.add("What is the primary intended purpose of the product?");
        }
        if (assessment.missingInformation().contains("whether the formulation is based on a recognized classical/traditional source")) {
            questions.add("Is this formulation based on a recognized classical Ayurvedic text or traditional formulation?");
        }
        if (assessment.conflicts().stream().anyMatch(conflict -> conflict.contains("food/nutraceutical"))) {
            questions.add("Is the product intended primarily as a medicine for treatment/prevention, or as a food/nutritional product?");
        }
        if (assessment.conflicts().stream().anyMatch(conflict -> conflict.contains("modified/proprietary"))) {
            questions.add("Is the formulation identical to a classical formulation, or has it been modified/proprietary?");
        }
        if (assessment.missingInformation().contains("target market or country")) {
            questions.add("What is the target market or country for the product?");
        }

        if (questions.isEmpty()) {
            questions.add("Is the product marketed as a medicine, food/nutraceutical, cosmetic, or new-drug-like botanical product?");
        }
        return questions.stream().limit(4).toList();
    }
}
