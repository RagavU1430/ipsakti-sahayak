package com.ipsakti.ip_sakti_backend.formulation.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DocumentGapItem(
        String documentArea,
        String status, // "AVAILABLE", "MISSING", "UNVERIFIED", "NOT_APPLICABLE"
        String importance, // "REQUIRED", "RECOMMENDED", "OPTIONAL"
        String reason,
        String authoritativeReference
) {
}
