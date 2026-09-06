package com.ipsakti.ip_sakti_backend.question.routing;

public record RoutingContext(QueryRoute previousRoute, QueryDomain previousDomain) {
    public static RoutingContext empty() {
        return new RoutingContext(null, null);
    }
}
