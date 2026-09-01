package com.ipsakti.ip_sakti_backend.regulatory.model;

import com.ipsakti.ip_sakti_backend.question.model.Jurisdiction;
import com.ipsakti.ip_sakti_backend.question.model.Language;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record RegulatoryAnalysisRequest(
        @NotBlank @Size(max = 160) String productName,
        @Size(max = 25) List<@NotBlank @Size(max = 120) String> ingredients,
        @Size(max = 80) String dosageForm,
        @Size(max = 500) String intendedUse,
        @Size(max = 20) List<@NotBlank @Size(max = 180) String> claims,
        Boolean traditionalKnowledge,
        @Size(max = 500) String classicalReference,
        Boolean biologicalResources,
        @Size(max = 160) String resourceOrigin,
        @Size(max = 160) String targetMarket,
        Jurisdiction jurisdiction,
        Boolean formulationNovelty,
        Boolean knownIngredients,
        Boolean synergisticEffectClaimed,
        Boolean geneticResources,
        Language language
) {
    public RegulatoryAnalysisRequest(
            String productName,
            List<String> ingredients,
            String dosageForm,
            String intendedUse,
            List<String> claims,
            Boolean traditionalKnowledge,
            String classicalReference,
            Boolean biologicalResources,
            String resourceOrigin,
            String targetMarket,
            Jurisdiction jurisdiction,
            Boolean formulationNovelty,
            Boolean knownIngredients,
            Boolean synergisticEffectClaimed,
            Boolean geneticResources
    ) {
        this(productName, ingredients, dosageForm, intendedUse, claims, traditionalKnowledge, classicalReference,
                biologicalResources, resourceOrigin, targetMarket, jurisdiction, formulationNovelty, knownIngredients,
                synergisticEffectClaimed, geneticResources, null);
    }

    public RegulatoryAnalysisRequest {
        productName = normalizeRequired(productName);
        ingredients = normalizeList(ingredients);
        dosageForm = normalizeNullable(dosageForm);
        intendedUse = normalizeNullable(intendedUse);
        claims = normalizeList(claims);
        classicalReference = normalizeNullable(classicalReference);
        resourceOrigin = normalizeNullable(resourceOrigin);
        targetMarket = normalizeNullable(targetMarket);
        if (jurisdiction == null) {
            jurisdiction = Jurisdiction.AUTO;
        }
    }

    public String combinedText() {
        return String.join(" ",
                productName,
                String.join(" ", ingredients),
                nullToEmpty(dosageForm),
                nullToEmpty(intendedUse),
                String.join(" ", claims),
                nullToEmpty(classicalReference),
                nullToEmpty(resourceOrigin),
                nullToEmpty(targetMarket)
        ).toLowerCase();
    }

    private static String normalizeRequired(String value) {
        if (value == null) {
            return null;
        }
        return String.join(" ", value.trim().split("\\s+"));
    }

    private static String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = String.join(" ", value.trim().split("\\s+"));
        return normalized.isBlank() ? null : normalized;
    }

    private static List<String> normalizeList(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream().map(RegulatoryAnalysisRequest::normalizeNullable).toList();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
