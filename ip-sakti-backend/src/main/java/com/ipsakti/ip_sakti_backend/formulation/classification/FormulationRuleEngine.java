package com.ipsakti.ip_sakti_backend.formulation.classification;

import com.ipsakti.ip_sakti_backend.formulation.model.FormulationClassification;
import com.ipsakti.ip_sakti_backend.formulation.model.FormulationRequest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class FormulationRuleEngine {

    public FormulationRuleAssessment assess(FormulationRequest request) {
        EnumMap<FormulationClassification, Integer> scores = new EnumMap<>(FormulationClassification.class);
        for (FormulationClassification classification : FormulationClassification.values()) {
            scores.put(classification, 0);
        }

        String text = normalizedText(request);

        addIf(scores, FormulationClassification.CLASSICAL_DRUG, 2,
                hasText(request.classicalReference()) || hasClassicalIdentitySignal(text));
        addIf(scores, FormulationClassification.CLASSICAL_DRUG, 1,
                Boolean.TRUE.equals(request.traditionalUse()) || containsAny(text, "traditional", "classical", "churna",
                        "arishta", "asava", "avaleha", "ghrita", "taila", "kwath"));

        addIf(scores, FormulationClassification.PATENT_PROPRIETARY, 2,
                containsAny(text, "proprietary", "modified", "novel combination", "new combination", "non-classical",
                        "own formulation", "unique blend"));
        addIf(scores, FormulationClassification.PATENT_PROPRIETARY, 1,
                containsAny(text, "patent", "ip protection", "commercial launch", "brand", "new process"));

        addIf(scores, FormulationClassification.PHYTOPHARMACEUTICAL_NEW_DRUG, 2,
                containsAny(text, "standardized extract", "active constituent", "active marker", "quantified",
                        "clinical trial", "new drug", "phytopharmaceutical"));
        addIf(scores, FormulationClassification.PHYTOPHARMACEUTICAL_NEW_DRUG, 1,
                containsAny(text, "plant derived active", "botanical extract", "therapeutic drug development",
                        "isolated compound"));

        addIf(scores, FormulationClassification.AYURVEDA_AAHAR_NUTRACEUTICAL, 2,
                containsAny(text, "food", "nutraceutical", "ayurveda aahar", "dietary", "nutrition", "beverage",
                        "snack", "health supplement"));
        addIf(scores, FormulationClassification.AYURVEDA_AAHAR_NUTRACEUTICAL, 1,
                containsAny(text, "supports", "wellness", "general health", "digestive support", "immunity support",
                        "daily consumption"));

        addIf(scores, FormulationClassification.COSMETIC, 2,
                containsAny(text, "cosmetic", "skin", "hair", "beauty", "appearance", "personal care", "topical",
                        "face cream", "shampoo", "lotion"));
        addIf(scores, FormulationClassification.COSMETIC, 1,
                containsAny(text, "glow", "fragrance", "cleansing", "moisturizing", "anti-dandruff", "fairness"));

        List<String> conflicts = conflicts(scores, text, request);
        List<String> missing = missingInformation(request);

        List<Map.Entry<FormulationClassification, Integer>> ranked = scores.entrySet().stream()
                .sorted(Map.Entry.<FormulationClassification, Integer>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(entry -> entry.getKey().name()))
                .toList();

        return new FormulationRuleAssessment(
                Map.copyOf(scores),
                ranked.getFirst().getKey(),
                ranked.getFirst().getValue(),
                ranked.size() > 1 ? ranked.get(1).getValue() : 0,
                conflicts,
                missing
        );
    }

    private String normalizedText(FormulationRequest request) {
        return String.join(" ",
                request.productName(),
                String.join(" ", request.ingredients()),
                nullToEmpty(request.dosageForm()),
                nullToEmpty(request.intendedUse()),
                String.join(" ", request.claims()),
                nullToEmpty(request.manufacturingMethod()),
                nullToEmpty(request.classicalReference()),
                nullToEmpty(request.targetMarket()),
                nullToEmpty(request.country()),
                nullToEmpty(request.existingLicense()),
                nullToEmpty(request.knownClassification())
        ).toLowerCase();
    }

    private List<String> conflicts(
            Map<FormulationClassification, Integer> scores,
            String text,
            FormulationRequest request
    ) {
        List<String> conflicts = new ArrayList<>();
        boolean therapeutic = containsAny(text, "treat", "treatment", "prevent", "cure", "manage disease", "disease",
                "therapeutic", "diabetes", "arthritis", "hypertension", "drug");
        boolean foodLike = scores.get(FormulationClassification.AYURVEDA_AAHAR_NUTRACEUTICAL) >= 2;
        boolean cosmetic = scores.get(FormulationClassification.COSMETIC) >= 2;
        boolean classical = scores.get(FormulationClassification.CLASSICAL_DRUG) >= 2;
        boolean modified = scores.get(FormulationClassification.PATENT_PROPRIETARY) >= 2;

        if (therapeutic && foodLike) {
            conflicts.add("The description includes both therapeutic/drug-like signals and food/nutraceutical positioning.");
        }
        if (therapeutic && cosmetic) {
            conflicts.add("The description includes both therapeutic and cosmetic/personal-care signals.");
        }
        if ((classical || hasText(request.classicalReference())) && modified) {
            conflicts.add("The description includes both classical-reference and modified/proprietary formulation signals.");
        }
        return conflicts;
    }

    private List<String> missingInformation(FormulationRequest request) {
        List<String> missing = new ArrayList<>();
        if (!hasText(request.intendedUse()) && request.claims().isEmpty()) {
            missing.add("primary intended use or claims");
        }
        if (!hasText(request.classicalReference()) && request.traditionalUse() == null) {
            missing.add("whether the formulation is based on a recognized classical/traditional source");
        }
        if (!hasText(request.targetMarket()) && !hasText(request.country())) {
            missing.add("target market or country");
        }
        return missing;
    }

    private void addIf(
            Map<FormulationClassification, Integer> scores,
            FormulationClassification classification,
            int points,
            boolean condition
    ) {
        if (condition) {
            scores.put(classification, scores.get(classification) + points);
        }
    }

    private boolean containsAny(String text, String... terms) {
        for (String term : terms) {
            if (text.contains(term)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasClassicalIdentitySignal(String text) {
        if (text.contains("non-classical")) {
            return containsAny(text, "classical text", "ashtanga", "charaka", "sushruta",
                    "bhavaprakasha", "sharangadhara");
        }
        return containsAny(text, "classical text", "ashtanga", "charaka", "sushruta",
                "bhavaprakasha", "sharangadhara", "classical formulation");
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
