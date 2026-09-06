package com.ipsakti.ip_sakti_backend.formulation.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record IpRouteAssessment(
        String route, // "PATENT", "TRADEMARK", "DESIGN", "COPYRIGHT", "GEOGRAPHICAL_INDICATION", "TRADE_SECRET", "PLANT_VARIETY"
        String relevance, // "HIGH", "MEDIUM", "LOW", "UNCLEAR"
        String reason,
        String evidence
) {
}
