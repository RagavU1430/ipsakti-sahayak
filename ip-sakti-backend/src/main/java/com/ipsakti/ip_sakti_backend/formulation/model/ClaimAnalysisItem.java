package com.ipsakti.ip_sakti_backend.formulation.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ClaimAnalysisItem(
        String claimText,
        ClaimCategory category,
        boolean hasAuthoritativeEvidence,
        String evidenceSource,
        String evidenceType,
        String regulatoryImplication
) {
}
