package com.ipsakti.ip_sakti_backend.regulatory.engine;

import com.ipsakti.ip_sakti_backend.question.model.Jurisdiction;
import com.ipsakti.ip_sakti_backend.rag.RagClient;
import com.ipsakti.ip_sakti_backend.rag.dto.RagAskRequest;
import com.ipsakti.ip_sakti_backend.rag.dto.RagAskResponse;
import com.ipsakti.ip_sakti_backend.regulatory.model.RegulatoryAnalysisRequest;
import com.ipsakti.ip_sakti_backend.regulatory.model.RegulatoryEngine;
import com.ipsakti.ip_sakti_backend.regulatory.model.RegulatoryEngineResult;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class Section3eAnalysisService {

    private final RagClient ragClient;
    private final RegulatoryJurisdictionRouter router;

    public Section3eAnalysisService(RagClient ragClient, RegulatoryJurisdictionRouter router) {
        this.ragClient = ragClient;
        this.router = router;
    }

    public RegulatoryEngineResult analyze(RegulatoryAnalysisRequest request, Jurisdiction jurisdiction) {
        RagAskResponse evidence = ragClient.ask(new RagAskRequest(
                "Indian patent law provisions concerning Section 3(e), mere admixture, aggregation, formulation composition, known ingredients and synergistic effect.",
                "PATENT",
                router.ragJurisdiction(jurisdiction),
                null
        ));
        int signals = signals(request);
        List<String> conflicts = conflicts(request);
        return Section3pAnalysisService.result(RegulatoryEngine.SECTION_3E, signals, conflicts, evidence,
                "Section 3(e) may be relevant where a formulation appears to involve known ingredients or an aggregation/mixture and the asserted technical effect needs review. This is not a patentability conclusion.");
    }

    private int signals(RegulatoryAnalysisRequest request) {
        int score = 0;
        String text = request.combinedText();
        if (Boolean.TRUE.equals(request.knownIngredients()) && request.ingredients().size() >= 2) score += 2;
        if (containsAny(text, "mere admixture", "aggregation", "combination", "mixture", "known ingredients")) score += 2;
        if (containsAny(text, "supports", "therapeutic", "cosmetic effect", "digestive health")) score += 1;
        if (Boolean.TRUE.equals(request.synergisticEffectClaimed())) score -= 1;
        return Math.max(0, score);
    }

    private List<String> conflicts(RegulatoryAnalysisRequest request) {
        List<String> conflicts = new ArrayList<>();
        if (Boolean.TRUE.equals(request.knownIngredients()) && Boolean.TRUE.equals(request.synergisticEffectClaimed())) {
            conflicts.add("known-ingredient/mixture signals are present, but a synergistic effect is also claimed");
        }
        return conflicts;
    }

    private boolean containsAny(String text, String... terms) {
        for (String term : terms) {
            if (text.contains(term)) return true;
        }
        return false;
    }
}
