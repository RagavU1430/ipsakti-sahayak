package com.ipsakti.ip_sakti_backend.regulatory.engine;

import com.ipsakti.ip_sakti_backend.question.model.Jurisdiction;
import com.ipsakti.ip_sakti_backend.rag.RagClient;
import com.ipsakti.ip_sakti_backend.rag.dto.RagAskRequest;
import com.ipsakti.ip_sakti_backend.rag.dto.RagAskResponse;
import com.ipsakti.ip_sakti_backend.regulatory.model.RegulatoryAnalysisRequest;
import com.ipsakti.ip_sakti_backend.regulatory.model.RegulatoryEngine;
import com.ipsakti.ip_sakti_backend.regulatory.model.RegulatoryEngineResult;
import com.ipsakti.ip_sakti_backend.regulatory.model.RegulatoryStatus;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AbsAnalysisService {

    private final RagClient ragClient;
    private final RegulatoryEvidenceMapper mapper;
    private final RegulatoryJurisdictionRouter router;

    public AbsAnalysisService(RagClient ragClient, RegulatoryEvidenceMapper mapper, RegulatoryJurisdictionRouter router) {
        this.ragClient = ragClient;
        this.mapper = mapper;
        this.router = router;
    }

    public RegulatoryEngineResult analyze(RegulatoryAnalysisRequest request, Jurisdiction jurisdiction) {
        RagAskResponse evidence = ragClient.ask(new RagAskRequest(
                "Indian biological diversity law provisions concerning access and benefit sharing, biological resources, NBA, SBB, commercial utilization and associated traditional knowledge.",
                "ABS",
                router.ragJurisdiction(jurisdiction),
                null
        ));
        if (Boolean.TRUE.equals(evidence.abstained())) {
            return new RegulatoryEngineResult(RegulatoryEngine.ABS, RegulatoryStatus.INSUFFICIENT_EVIDENCE, Math.min(0.35, evidence.confidence()),
                    "The RAG evidence was insufficient for ABS analysis.", List.of(), null,
                    mapper.citations(evidence.citations()), mapper.sources(evidence.sources()));
        }

        int signals = signals(request);
        List<String> considerations = considerations(request);
        RegulatoryStatus status = signals >= 2 ? RegulatoryStatus.POTENTIALLY_APPLICABLE : RegulatoryStatus.NOT_INDICATED;
        if (request.biologicalResources() == null || request.resourceOrigin() == null) {
            considerations.add("Resource involvement/origin information is incomplete.");
            if (signals > 0) status = RegulatoryStatus.REVIEW_RECOMMENDED;
        }
        double confidence = Section3pAnalysisService.confidence(signals, considerations.size() > 1 ? 1 : 0, evidence);
        String reason = status == RegulatoryStatus.NOT_INDICATED
                ? "The structured inputs do not currently indicate biological-resource/ABS issues."
                : "Retrieved sources identify ABS considerations that may apply depending on biological resource origin, access, and intended use.";
        return new RegulatoryEngineResult(RegulatoryEngine.ABS, status, confidence, reason, considerations, null,
                mapper.citations(evidence.citations()), mapper.sources(evidence.sources()));
    }

    private int signals(RegulatoryAnalysisRequest request) {
        int score = 0;
        String text = request.combinedText();
        if (Boolean.TRUE.equals(request.biologicalResources())) score += 2;
        if (Boolean.TRUE.equals(request.traditionalKnowledge())) score += 1;
        if (request.resourceOrigin() != null && request.resourceOrigin().toLowerCase().contains("india")) score += 1;
        if (containsAny(text, "biological resource", "bioresource", "plant resource", "nba", "sbb", "benefit sharing", "abs")) score += 1;
        return score;
    }

    private List<String> considerations(RegulatoryAnalysisRequest request) {
        List<String> out = new ArrayList<>();
        if (Boolean.TRUE.equals(request.biologicalResources())) out.add("Biological resource involvement is indicated.");
        if (Boolean.TRUE.equals(request.traditionalKnowledge())) out.add("Associated traditional knowledge is indicated.");
        if (request.resourceOrigin() != null) out.add("Resource origin supplied: " + request.resourceOrigin() + ".");
        return out;
    }

    private boolean containsAny(String text, String... terms) {
        for (String term : terms) {
            if (text.contains(term)) return true;
        }
        return false;
    }
}
