package com.ipsakti.ip_sakti_backend.formulation.model;

import java.util.List;

public record RegulatoryRoute(
        String route,
        List<String> domains,
        String jurisdiction
) {
}
