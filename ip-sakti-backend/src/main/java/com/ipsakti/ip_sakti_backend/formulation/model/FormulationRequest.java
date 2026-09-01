package com.ipsakti.ip_sakti_backend.formulation.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.ipsakti.ip_sakti_backend.question.model.Language;
import java.util.List;

public record FormulationRequest(
        @NotBlank @Size(max = 160) String productName,
        @Size(max = 25) List<@NotBlank @Size(max = 120) String> ingredients,
        @Size(max = 80) String dosageForm,
        @Size(max = 500) String intendedUse,
        @Size(max = 20) List<@NotBlank @Size(max = 180) String> claims,
        @Size(max = 500) String manufacturingMethod,
        @Size(max = 500) String classicalReference,
        Boolean traditionalUse,
        Boolean commercialIntent,
        @Size(max = 120) String targetMarket,
        @Size(max = 120) String country,
        @Size(max = 160) String existingLicense,
        @Size(max = 160) String knownClassification,
        Language language
) {
    public FormulationRequest(
            String productName,
            List<String> ingredients,
            String dosageForm,
            String intendedUse,
            List<String> claims,
            String manufacturingMethod,
            String classicalReference,
            Boolean traditionalUse,
            Boolean commercialIntent,
            String targetMarket,
            String country,
            String existingLicense,
            String knownClassification
    ) {
        this(productName, ingredients, dosageForm, intendedUse, claims, manufacturingMethod, classicalReference,
                traditionalUse, commercialIntent, targetMarket, country, existingLicense, knownClassification, null);
    }

    public FormulationRequest {
        productName = normalizeRequired(productName);
        ingredients = normalizeList(ingredients);
        dosageForm = normalizeNullable(dosageForm);
        intendedUse = normalizeNullable(intendedUse);
        claims = normalizeList(claims);
        manufacturingMethod = normalizeNullable(manufacturingMethod);
        classicalReference = normalizeNullable(classicalReference);
        targetMarket = normalizeNullable(targetMarket);
        country = normalizeNullable(country);
        existingLicense = normalizeNullable(existingLicense);
        knownClassification = normalizeNullable(knownClassification);
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
        return values.stream()
                .map(FormulationRequest::normalizeNullable)
                .toList();
    }
}
