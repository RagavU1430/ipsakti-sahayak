package com.ipsakti.ip_sakti_backend.formulation.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReadinessScores(
        String regulatoryClassification, // "CONFIRMED", "LIKELY", "UNCLEAR"
        String documentCompleteness,     // "COMPLETE", "PARTIAL", "INSUFFICIENT_EVIDENCE"
        String claims,                   // "LOW_CONCERN", "REVIEW_REQUIRED", "HIGH_REVIEW_REQUIRED"
        String ingredientVerification,   // "VERIFIED", "PARTIAL", "INSUFFICIENT"
        String tk,                       // "NONE_IDENTIFIED", "POTENTIAL", "CONFIRMED", "INSUFFICIENT_EVIDENCE"
        String abs,                      // "NOT_INDICATED", "POTENTIALLY_RELEVANT", "REQUIRES_REVIEW"
        String ip,                       // "POTENTIAL_ROUTES_IDENTIFIED", "REVIEW_REQUIRED", "INSUFFICIENT"
        String overall                   // "LOW_RISK_FOR_NEXT_REVIEW", "REQUIRES_DOCUMENT_COMPLETION", "REQUIRES_REGULATORY_REVIEW", "REQUIRES_IP_REVIEW", "INSUFFICIENT_EVIDENCE"
) {
}
