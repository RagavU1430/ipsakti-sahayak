package com.ipsakti.ip_sakti_backend.question.classification;

import com.ipsakti.ip_sakti_backend.question.model.Jurisdiction;
import com.ipsakti.ip_sakti_backend.question.model.QuestionIntent;
import org.springframework.stereotype.Component;

@Component
public class JurisdictionResolver {

    public Jurisdiction resolve(Jurisdiction requested, QuestionIntent intent, String question) {
        if (requested == Jurisdiction.INDIA || requested == Jurisdiction.INTERNATIONAL) {
            return requested;
        }

        String normalized = question == null ? "" : question.toLowerCase();
        if (intent == QuestionIntent.INTERNATIONAL_IP
                || containsAny(normalized, "wipo", "trips", "paris convention", "pct", "madrid protocol",
                "budapest treaty", "gratk", "international")) {
            return Jurisdiction.INTERNATIONAL;
        }
        if (containsAny(normalized, "india", "indian", "patents act", "trade marks act", "copyright act",
                "designs act", "biological diversity act", "ayush", "fssai", "nba", "sbb")) {
            return Jurisdiction.INDIA;
        }
        return Jurisdiction.AUTO;
    }

    public String ragJurisdictionFor(Jurisdiction jurisdiction) {
        if (jurisdiction == Jurisdiction.INDIA || jurisdiction == Jurisdiction.INTERNATIONAL) {
            return jurisdiction.name();
        }
        return null;
    }

    private boolean containsAny(String text, String... candidates) {
        for (String candidate : candidates) {
            if (text.contains(candidate)) {
                return true;
            }
        }
        return false;
    }
}
