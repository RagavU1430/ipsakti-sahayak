package com.ipsakti.ip_sakti_backend.question.routing;

public record RoutingDecision(
        QueryRoute route,
        QueryDomain domain,
        double confidence,
        RoutingReason reason,
        boolean authoritativeEvidenceRequired,
        boolean documentReferenced
) {
}
