package com.ipsakti.ip_sakti_backend.question.routing;

import com.ipsakti.ip_sakti_backend.question.model.Jurisdiction;
import com.ipsakti.ip_sakti_backend.question.model.Language;

public interface QueryRouter {
    RoutingDecision route(String canonicalQuery, Language requestedLanguage, Jurisdiction jurisdiction, RoutingContext context);
}
