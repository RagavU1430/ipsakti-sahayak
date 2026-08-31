package com.ipsakti.ip_sakti_backend.regulatory.engine;

import com.ipsakti.ip_sakti_backend.question.model.Jurisdiction;
import com.ipsakti.ip_sakti_backend.regulatory.model.RegulatoryAnalysisRequest;
import org.springframework.stereotype.Component;

@Component
public class RegulatoryJurisdictionRouter {

    public Jurisdiction resolve(RegulatoryAnalysisRequest request) {
        if (request.jurisdiction() == Jurisdiction.INDIA || request.jurisdiction() == Jurisdiction.INTERNATIONAL) {
            return request.jurisdiction();
        }
        String text = request.combinedText();
        if (containsAny(text, "india", "indian", "ayush", "nba", "sbb", "biological diversity act")) {
            return Jurisdiction.INDIA;
        }
        if (containsAny(text, "international", "wipo", "gratk", "trips", "pct", "global", "europe", "usa")) {
            return Jurisdiction.INTERNATIONAL;
        }
        return Jurisdiction.AUTO;
    }

    public boolean ambiguous(Jurisdiction jurisdiction) {
        return jurisdiction == Jurisdiction.AUTO;
    }

    public String ragJurisdiction(Jurisdiction jurisdiction) {
        if (jurisdiction == Jurisdiction.INTERNATIONAL) {
            return "INTERNATIONAL";
        }
        return "INDIA";
    }

    private boolean containsAny(String text, String... terms) {
        for (String term : terms) {
            if (text.contains(term)) {
                return true;
            }
        }
        return false;
    }
}
