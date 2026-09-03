package com.ipsakti.ip_sakti_backend.tk.analysis;

import com.ipsakti.ip_sakti_backend.rag.dto.RagAskResponse;
import com.ipsakti.ip_sakti_backend.rag.dto.RagCitation;
import com.ipsakti.ip_sakti_backend.rag.dto.RagSource;
import com.ipsakti.ip_sakti_backend.tk.model.TkOverlapClassification;
import com.ipsakti.ip_sakti_backend.tk.model.TkOverlapType;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class TkEvidenceAnalyzer {

    public TkAssessment assess(String canonicalDescription, TkQueryAnalysis query, RagAskResponse ragResponse) {
        List<RagCitation> citations = ragResponse.citations() == null ? List.of() : ragResponse.citations();
        List<RagSource> sources = ragResponse.sources() == null ? List.of() : ragResponse.sources();
        double ragConfidence = ragResponse.confidence() == null ? 0.0 : ragResponse.confidence();

        if (Boolean.TRUE.equals(ragResponse.abstained()) || citations.isEmpty() || sources.isEmpty() || ragConfidence < 0.35) {
            return insufficient(ragConfidence);
        }

        Set<TkOverlapType> overlapTypes = overlapTypes(canonicalDescription, query, ragResponse);
        boolean tkEvidencePresent = evidenceMentionsTk(ragResponse);
        if (overlapTypes.isEmpty() || !tkEvidencePresent) {
            double confidence = round(Math.min(0.55, Math.max(0.30, ragConfidence * 0.55)));
            return new TkAssessment(
                    TkOverlapClassification.NO_TK_OVERLAP_FOUND,
                    confidence,
                    List.of(),
                    false,
                    "The retrieved authoritative evidence did not support a specific traditional-knowledge overlap for the submitted description.",
                    "Treat this as no overlap found in the current corpus, not as proof that no traditional knowledge exists elsewhere."
            );
        }

        int dimensions = overlapTypes.size();
        int citationCount = citations.size();
        double sourceStrength = sources.stream().mapToDouble(s -> s.score() == null ? 0.0 : s.score()).max().orElse(0.0);
        double confidence = round(Math.min(0.94,
                (ragConfidence * 0.55)
                        + (Math.min(dimensions, 4) * 0.07)
                        + (Math.min(citationCount, 3) * 0.04)
                        + (Math.min(sourceStrength, 1.0) * 0.08)));

        boolean strong = dimensions >= 3 && citationCount >= 2 && confidence >= 0.78;
        TkOverlapClassification classification = strong
                ? TkOverlapClassification.STRONG_TK_OVERLAP
                : TkOverlapClassification.POTENTIAL_TK_OVERLAP;

        String explanation = strong
                ? "Multiple evidence dimensions correspond to traditional-knowledge indicators in the submitted description. This is a system-generated overlap assessment, not a legal validity determination."
                : "Relevant traditional-knowledge evidence was retrieved, but the overlap is partial or should be reviewed with caution. This is a preliminary evidence-backed assessment.";
        String recommendation = strong
                ? "Review patentability, traditional-knowledge, biological-resource, and disclosure issues with qualified counsel before filing or commercialization."
                : "Review the cited evidence and consider a professional freedom-to-operate or patentability review if the product or invention will be commercialized.";

        return new TkAssessment(classification, confidence, new ArrayList<>(overlapTypes), false, explanation, recommendation);
    }

    private TkAssessment insufficient(double ragConfidence) {
        return new TkAssessment(
                TkOverlapClassification.INSUFFICIENT_EVIDENCE,
                round(Math.min(0.20, Math.max(0.10, ragConfidence))),
                List.of(),
                true,
                "Insufficient authoritative evidence was found in the current corpus to assess traditional-knowledge overlap reliably.",
                "Provide more detail about ingredients, biological resources, preparation method, claimed use, geography, and any traditional references."
        );
    }

    private Set<TkOverlapType> overlapTypes(String canonicalDescription, TkQueryAnalysis query, RagAskResponse ragResponse) {
        String text = (canonicalDescription + " " + ragResponse.answer()).toLowerCase(Locale.ROOT);
        Set<TkOverlapType> types = new LinkedHashSet<>();
        if (!query.ingredients().isEmpty() && containsAny(text, "plant", "herb", "turmeric", "neem", "tulsi", "extract", "ingredient")) {
            types.add(TkOverlapType.INGREDIENT_OVERLAP);
        }
        if (!query.traditionalUseTerms().isEmpty() && containsAny(text, "traditional", "knowledge", "ayurveda", "siddha", "unani", "medicinal")) {
            types.add(TkOverlapType.TRADITIONAL_USE_OVERLAP);
        }
        if (containsAny(text, "formulation", "composition", "combination", "mixture")) {
            types.add(TkOverlapType.FORMULATION_OVERLAP);
        }
        if (!query.preparationMethods().isEmpty()) {
            types.add(TkOverlapType.PREPARATION_METHOD_OVERLAP);
        }
        if (containsAny(text, "process", "method", "preparation")) {
            types.add(TkOverlapType.PROCESS_OVERLAP);
        }
        if (!query.tkIndicators().isEmpty() || containsAny(text, "section 3(p)", "traditional knowledge", "tkdl", "gratk")) {
            types.add(TkOverlapType.KNOWLEDGE_DOMAIN_OVERLAP);
        }
        if (!query.geographicIndicators().isEmpty() && containsAny(text, "india", "indian", "community", "geographical")) {
            types.add(TkOverlapType.GEOGRAPHIC_OR_COMMUNITY_OVERLAP);
        }
        if (!query.biologicalResources().isEmpty() && containsAny(text, "biological", "biodiversity", "genetic resource", "plant", "botanical")) {
            types.add(TkOverlapType.BIOLOGICAL_RESOURCE_OVERLAP);
        }
        return types;
    }

    private boolean evidenceMentionsTk(RagAskResponse ragResponse) {
        String answer = ragResponse.answer() == null ? "" : ragResponse.answer().toLowerCase(Locale.ROOT);
        boolean answerMentionsTk = containsAny(answer,
                "traditional knowledge", "section 3(p)", "ayurveda", "ayurvedic", "biodiversity",
                "biological resource", "genetic resource", "gratk", "tkdl", "known properties", "known use");
        boolean sourceMentionsTk = (ragResponse.sources() == null ? List.<RagSource>of() : ragResponse.sources()).stream()
                .map(RagSource::documentId)
                .filter(id -> id != null)
                .map(id -> id.toLowerCase(Locale.ROOT))
                .anyMatch(id -> id.contains("bd") || id.contains("pat") || id.contains("wipo") || id.contains("gratk"));
        return answerMentionsTk || sourceMentionsTk;
    }

    private boolean containsAny(String text, String... terms) {
        for (String term : terms) {
            if (text.contains(term)) {
                return true;
            }
        }
        return false;
    }

    private double round(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }
}
