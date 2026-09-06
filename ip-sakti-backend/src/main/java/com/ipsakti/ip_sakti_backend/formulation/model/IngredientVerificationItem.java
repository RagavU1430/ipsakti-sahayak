package com.ipsakti.ip_sakti_backend.formulation.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record IngredientVerificationItem(
        String suppliedName,
        String botanicalName,
        String quantityRatio,
        String formulationRole,
        String evidenceInAuthoritativeSources,
        String traditionalUseEvidence,
        boolean biologicalResourceRelevance,
        String status, // "VERIFIED", "PARTIAL", "UNVERIFIED"
        String uncertainty
) {
}
