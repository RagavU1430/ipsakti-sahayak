package com.ipsakti.ip_sakti_backend.regulatory.engine;

import com.ipsakti.ip_sakti_backend.question.model.Jurisdiction;
import com.ipsakti.ip_sakti_backend.rag.RagClient;
import com.ipsakti.ip_sakti_backend.rag.dto.RagAskRequest;
import com.ipsakti.ip_sakti_backend.rag.dto.RagAskResponse;
import com.ipsakti.ip_sakti_backend.regulatory.model.GratkResourceType;
import com.ipsakti.ip_sakti_backend.regulatory.model.RegulatoryAnalysisRequest;
import com.ipsakti.ip_sakti_backend.regulatory.model.RegulatoryEngine;
import com.ipsakti.ip_sakti_backend.regulatory.model.RegulatoryEngineResult;
import com.ipsakti.ip_sakti_backend.regulatory.model.RegulatoryStatus;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class GratkAnalysisService {

    private final RagClient ragClient;
    private final RegulatoryEvidenceMapper mapper;
    private final RegulatoryJurisdictionRouter router;

    public GratkAnalysisService(RagClient ragClient, RegulatoryEvidenceMapper mapper, RegulatoryJurisdictionRouter router) {
        this.ragClient = ragClient;
        this.mapper = mapper;
        this.router = router;
    }

    public RegulatoryEngineResult analyze(RegulatoryAnalysisRequest request, Jurisdiction jurisdiction) {
        RagAskResponse evidence = ragClient.ask(new RagAskRequest(
                "International and Indian provisions concerning genetic resources, traditional knowledge, associated traditional knowledge and WIPO GRATK disclosure considerations.",
                jurisdiction == Jurisdiction.INTERNATIONAL ? "INTERNATIONAL" : "ABS",
                router.ragJurisdiction(jurisdiction),
                null
        ));
        GratkResourceType type = typeFor(request);
        if (Boolean.TRUE.equals(evidence.abstained())) {
            return new RegulatoryEngineResult(RegulatoryEngine.GRATK, RegulatoryStatus.INSUFFICIENT_EVIDENCE, Math.min(0.35, evidence.confidence()),
                    "The RAG evidence was insufficient for GRATK analysis.", List.of(), type,
                    mapper.citations(evidence.citations()), mapper.sources(evidence.sources()));
        }
        int signals = switch (type) {
            case GENETIC_RESOURCE_AND_ASSOCIATED_TK -> 4;
            case GENETIC_RESOURCE, TRADITIONAL_KNOWLEDGE -> 2;
            case UNKNOWN -> 1;
            case NONE -> 0;
        };
        RegulatoryStatus status = signals >= 2 ? RegulatoryStatus.REVIEW_RECOMMENDED : RegulatoryStatus.NOT_INDICATED;
        double confidence = Section3pAnalysisService.confidence(signals, 0, evidence);
        String reason = switch (type) {
            case GENETIC_RESOURCE_AND_ASSOCIATED_TK -> "The input indicates both genetic/biological resource use and associated traditional knowledge, so GRATK-style disclosure/review considerations may be relevant.";
            case GENETIC_RESOURCE -> "The input indicates genetic/biological resource use without clear associated traditional knowledge.";
            case TRADITIONAL_KNOWLEDGE -> "The input indicates traditional knowledge without clear genetic-resource utilization.";
            case UNKNOWN -> "The input is incomplete on genetic-resource and traditional-knowledge involvement.";
            case NONE -> "The structured inputs do not currently indicate genetic-resource or associated traditional-knowledge issues.";
        };
        return new RegulatoryEngineResult(RegulatoryEngine.GRATK, status, confidence, reason, List.of(), type,
                mapper.citations(evidence.citations()), mapper.sources(evidence.sources()));
    }

    private GratkResourceType typeFor(RegulatoryAnalysisRequest request) {
        boolean gr = Boolean.TRUE.equals(request.geneticResources()) || Boolean.TRUE.equals(request.biologicalResources())
                || containsAny(request.combinedText(), "genetic resource", "biological resource", "plant resource");
        boolean tk = Boolean.TRUE.equals(request.traditionalKnowledge()) || request.classicalReference() != null
                || containsAny(request.combinedText(), "traditional knowledge", "classical", "traditional use");
        if (gr && tk) return GratkResourceType.GENETIC_RESOURCE_AND_ASSOCIATED_TK;
        if (gr) return GratkResourceType.GENETIC_RESOURCE;
        if (tk) return GratkResourceType.TRADITIONAL_KNOWLEDGE;
        if (request.geneticResources() == null && request.biologicalResources() == null && request.traditionalKnowledge() == null) {
            return GratkResourceType.UNKNOWN;
        }
        return GratkResourceType.NONE;
    }

    private boolean containsAny(String text, String... terms) {
        for (String term : terms) {
            if (text.contains(term)) return true;
        }
        return false;
    }
}
