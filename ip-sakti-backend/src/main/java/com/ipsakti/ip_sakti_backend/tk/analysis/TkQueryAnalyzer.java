package com.ipsakti.ip_sakti_backend.tk.analysis;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class TkQueryAnalyzer {

    private static final List<String> INGREDIENT_HINTS = List.of(
            "turmeric", "curcuma", "neem", "azadirachta", "tulsi", "holy basil", "ocimum",
            "ashwagandha", "withania", "amla", "emblica", "brahmi", "bacopa", "ginger",
            "zingiber", "pepper", "piper", "sandalwood", "aloe", "honey", "herb", "herbal",
            "plant", "botanical", "extract", "root", "leaf", "bark", "seed", "oil"
    );

    private static final List<String> TRADITIONAL_USE_HINTS = List.of(
            "traditional use", "traditional knowledge", "folk", "community knowledge", "known use",
            "ayurveda", "ayurvedic", "siddha", "unani", "home remedy", "medicinal use",
            "therapeutic use", "classical reference", "ancient", "indigenous"
    );

    private static final List<String> PREPARATION_HINTS = List.of(
            "decoction", "paste", "powder", "extract", "fermentation", "distillation",
            "infusion", "boiling", "oil preparation", "churna", "kwath", "taila", "lehyam"
    );

    private static final List<String> GEOGRAPHIC_HINTS = List.of(
            "india", "indian", "kerala", "tamil nadu", "karnataka", "andhra", "telangana",
            "himalayan", "tribal", "community", "village", "indigenous community"
    );

    private static final List<String> TK_HINTS = List.of(
            "tkdl", "section 3(p)", "biological diversity", "biodiversity", "abs", "gratk",
            "genetic resource", "biological resource", "geographical indication"
    );

    public TkQueryAnalysis analyze(String description) {
        String normalized = description == null ? "" : description.toLowerCase(Locale.ROOT);
        return new TkQueryAnalysis(
                matches(normalized, INGREDIENT_HINTS),
                matches(normalized, TRADITIONAL_USE_HINTS),
                matches(normalized, PREPARATION_HINTS),
                matches(normalized, GEOGRAPHIC_HINTS),
                matches(normalized, TK_HINTS),
                biologicalResources(normalized)
        );
    }

    private List<String> biologicalResources(String text) {
        Set<String> values = new LinkedHashSet<>();
        if (containsAny(text, "plant", "herb", "botanical", "extract", "root", "leaf", "seed", "bark", "oil")) {
            values.add("plant_or_botanical_resource");
        }
        if (containsAny(text, "genetic resource", "biological resource", "biodiversity", "abs")) {
            values.add("biological_resource");
        }
        return new ArrayList<>(values);
    }

    private List<String> matches(String text, List<String> hints) {
        Set<String> values = new LinkedHashSet<>();
        for (String hint : hints) {
            if (text.contains(hint)) {
                values.add(hint);
            }
        }
        return new ArrayList<>(values);
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
