package com.ipsakti.ip_sakti_backend.question.classification;

import com.ipsakti.ip_sakti_backend.question.model.QuestionIntent;
import org.springframework.stereotype.Component;

@Component
public class QuestionIntentClassifier {

    public QuestionIntent classify(String question) {
        String normalized = question == null ? "" : question.toLowerCase();

        if (containsAny(normalized, "wipo", "trips", "paris convention", "pct", "madrid protocol",
                "budapest treaty", "gratk", "international")) {
            return QuestionIntent.INTERNATIONAL_IP;
        }
        if (containsAny(normalized, "section 3(p)", "section 3p", "3(p)", "section 3(e)", "section 3e", "3(e)",
                "patent", "patentability", "tkdl", "traditional knowledge", "novelty", "inventive step")) {
            return QuestionIntent.PATENT;
        }
        if (containsAny(normalized, "trademark", "trade mark", "brand", "logo")) {
            return QuestionIntent.TRADEMARK;
        }
        if (containsAny(normalized, "copyright", "author", "literary", "artistic", "song", "software")) {
            return QuestionIntent.COPYRIGHT;
        }
        if (containsAny(normalized, "design", "industrial design")) {
            return QuestionIntent.DESIGN;
        }
        if (containsAny(normalized, "geographical indication", " gi ", "gi registration")) {
            return QuestionIntent.GI;
        }
        if (containsAny(normalized, "plant variety", "ppvfr", "farmer rights", "breeder")) {
            return QuestionIntent.PLANT_VARIETY;
        }
        if (containsAny(normalized, "biodiversity", "biological diversity", "abs", "access and benefit sharing",
                "nba", "sbb", "bio-resource", "bioresource")) {
            return QuestionIntent.BIODIVERSITY_ABS;
        }
        if (containsAny(normalized, "ayurveda", "ayurvedic", "ayush", "classical formulation",
                "proprietary medicine", "aahar", "nutraceutical", "fssai", "cosmetic")) {
            return QuestionIntent.AYURVEDA_REGULATION;
        }
        if (containsAny(normalized, "ip ", "intellectual property", "registration", "infringement", "licence", "license")) {
            return QuestionIntent.IP_GENERAL;
        }
        return QuestionIntent.GENERAL;
    }

    public String ragDomainFor(QuestionIntent intent) {
        return switch (intent) {
            case PATENT -> "PATENT";
            case TRADEMARK -> "TRADEMARK";
            case COPYRIGHT -> "COPYRIGHT";
            case DESIGN -> "DESIGN";
            case GI -> "GI";
            case PLANT_VARIETY -> "PLANT_VARIETY";
            case BIODIVERSITY_ABS -> "ABS";
            case AYURVEDA_REGULATION -> "AYURVEDA";
            case INTERNATIONAL_IP -> "INTERNATIONAL";
            case IP_GENERAL, GENERAL -> null;
        };
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
