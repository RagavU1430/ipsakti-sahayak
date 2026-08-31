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
public class Section3pAnalysisService {

    private final RagClient ragClient;
    private final RegulatoryEvidenceMapper mapper;
    private final RegulatoryJurisdictionRouter router;

    public Section3pAnalysisService(RagClient ragClient, RegulatoryEvidenceMapper mapper, RegulatoryJurisdictionRouter router) {
        this.ragClient = ragClient;
        this.mapper = mapper;
        this.router = router;
    }

    public RegulatoryEngineResult analyze(RegulatoryAnalysisRequest request, Jurisdiction jurisdiction) {
        RagAskResponse evidence = ragClient.ask(new RagAskRequest(
                "Relevant Indian patent law provisions concerning Section 3(p), traditional knowledge, known traditional use, classical Ayurvedic knowledge and patentability review.",
                "PATENT",
                router.ragJurisdiction(jurisdiction),
                null
        ));
        int signals = signals(request);
        List<String> conflicts = conflicts(request);
        return result(RegulatoryEngine.SECTION_3P, signals, conflicts, evidence,
                "Section 3(p) may be relevant when the invention/formulation is tied to traditional knowledge, classical references, or known traditional use. This is a review flag, not a rejection conclusion.");
    }

    private int signals(RegulatoryAnalysisRequest request) {
        int score = 0;
        String text = request.combinedText();
        if (Boolean.TRUE.equals(request.traditionalKnowledge())) score += 2;
        if (request.classicalReference() != null) score += 2;
        if (containsAny(text, "traditional knowledge", "classical", "traditional use", "tkdl", "known traditional")) score += 1;
        if (Boolean.TRUE.equals(request.formulationNovelty())) score += 1;
        return score;
    }

    private List<String> conflicts(RegulatoryAnalysisRequest request) {
        List<String> conflicts = new ArrayList<>();
        if (Boolean.FALSE.equals(request.traditionalKnowledge()) && request.classicalReference() != null) {
            conflicts.add("traditionalKnowledge is false but a classical reference was supplied");
        }
        return conflicts;
    }

    static RegulatoryEngineResult result(RegulatoryEngine engine, int signals, List<String> conflicts, RagAskResponse evidence, String reviewReason) {
        var mapper = new RegulatoryEvidenceMapper();
        if (Boolean.TRUE.equals(evidence.abstained())) {
            return new RegulatoryEngineResult(engine, RegulatoryStatus.INSUFFICIENT_EVIDENCE, Math.min(0.35, evidence.confidence()),
                    "The RAG evidence was insufficient for this engine, so no legal/regulatory conclusion is suggested.",
                    List.of(), null, mapper.citations(evidence.citations()), mapper.sources(evidence.sources()));
        }
        double confidence = confidence(signals, conflicts.size(), evidence);
        RegulatoryStatus status = signals >= 2 ? RegulatoryStatus.REVIEW_RECOMMENDED : RegulatoryStatus.NOT_INDICATED;
        String reason = status == RegulatoryStatus.REVIEW_RECOMMENDED ? reviewReason
                : "Structured inputs do not currently indicate this issue, but retrieved evidence is preserved for review.";
        if (!conflicts.isEmpty()) {
            status = RegulatoryStatus.REVIEW_RECOMMENDED;
            reason = "Conflicting input signals require clarification: " + String.join("; ", conflicts) + ".";
        }
        return new RegulatoryEngineResult(engine, status, confidence, reason, conflicts, null,
                mapper.citations(evidence.citations()), mapper.sources(evidence.sources()));
    }

    static double confidence(int signals, int conflicts, RagAskResponse evidence) {
        double score = 0.25 + Math.min(signals, 4) * 0.12 + evidence.confidence() * 0.25
                + (evidence.citations().isEmpty() ? 0.0 : 0.08) - conflicts * 0.18;
        return Math.round(Math.max(0.0, Math.min(1.0, score)) * 10000.0) / 10000.0;
    }

    private boolean containsAny(String text, String... terms) {
        for (String term : terms) {
            if (text.contains(term)) return true;
        }
        return false;
    }
}
