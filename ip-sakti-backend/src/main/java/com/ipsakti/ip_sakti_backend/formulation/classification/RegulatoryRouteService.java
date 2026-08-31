package com.ipsakti.ip_sakti_backend.formulation.classification;

import com.ipsakti.ip_sakti_backend.formulation.model.FormulationClassification;
import com.ipsakti.ip_sakti_backend.formulation.model.RegulatoryRoute;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class RegulatoryRouteService {

    public RegulatoryRoute routeFor(FormulationClassification classification, String jurisdiction) {
        if (classification == null) {
            return null;
        }
        return switch (classification) {
            case CLASSICAL_DRUG -> new RegulatoryRoute("AYUSH_CLASSICAL_DRUG", List.of("AYURVEDA"), jurisdiction);
            case PATENT_PROPRIETARY -> new RegulatoryRoute("AYUSH_PATENT_IP", List.of("AYURVEDA", "PATENT"), jurisdiction);
            case PHYTOPHARMACEUTICAL_NEW_DRUG -> new RegulatoryRoute("PHYTOPHARMACEUTICAL_NEW_DRUG", List.of("AYURVEDA"), jurisdiction);
            case AYURVEDA_AAHAR_NUTRACEUTICAL -> new RegulatoryRoute("AYURVEDA_AAHAR", List.of("AYURVEDA", "FOOD"), jurisdiction);
            case COSMETIC -> new RegulatoryRoute("COSMETIC_REGULATORY", List.of("AYURVEDA"), jurisdiction);
        };
    }
}
