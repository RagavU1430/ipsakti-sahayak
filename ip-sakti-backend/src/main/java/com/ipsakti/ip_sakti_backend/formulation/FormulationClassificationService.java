package com.ipsakti.ip_sakti_backend.formulation;

import com.ipsakti.ip_sakti_backend.formulation.classification.FormulationClarificationService;
import com.ipsakti.ip_sakti_backend.formulation.classification.FormulationRuleAssessment;
import com.ipsakti.ip_sakti_backend.formulation.classification.FormulationRuleEngine;
import com.ipsakti.ip_sakti_backend.formulation.classification.RegulatoryRouteService;
import com.ipsakti.ip_sakti_backend.formulation.model.FormulationClassification;
import com.ipsakti.ip_sakti_backend.formulation.model.FormulationRequest;
import com.ipsakti.ip_sakti_backend.formulation.model.FormulationResponse;
import com.ipsakti.ip_sakti_backend.formulation.model.FormulationStatus;
import com.ipsakti.ip_sakti_backend.formulation.model.RegulatoryRoute;
import com.ipsakti.ip_sakti_backend.question.model.QuestionCitation;
import com.ipsakti.ip_sakti_backend.question.model.QuestionSource;
import com.ipsakti.ip_sakti_backend.rag.RagClient;
import com.ipsakti.ip_sakti_backend.rag.dto.RagAskRequest;
import com.ipsakti.ip_sakti_backend.rag.dto.RagAskResponse;
import com.ipsakti.ip_sakti_backend.rag.dto.RagCitation;
import com.ipsakti.ip_sakti_backend.rag.dto.RagSource;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class FormulationClassificationService {

    private static final Logger log = LoggerFactory.getLogger(FormulationClassificationService.class);

    private final RagClient ragClient;
    private final FormulationRuleEngine ruleEngine;
    private final FormulationClarificationService clarificationService;
    private final RegulatoryRouteService routeService;

    public FormulationClassificationService(
            RagClient ragClient,
            FormulationRuleEngine ruleEngine,
            FormulationClarificationService clarificationService,
            RegulatoryRouteService routeService
    ) {
        this.ragClient = ragClient;
        this.ruleEngine = ruleEngine;
        this.clarificationService = clarificationService;
        this.routeService = routeService;
    }

    public FormulationResponse classify(FormulationRequest request) {
        long started = System.nanoTime();
        String requestId = UUID.randomUUID().toString();
        FormulationRuleAssessment assessment = ruleEngine.assess(request);
        String jurisdiction = formulationJurisdiction(request);
        RagAskResponse ragResponse = ragClient.ask(new RagAskRequest(
                ragQueryFor(request, assessment),
                "AYURVEDA",
                "INTERNATIONAL".equals(jurisdiction) ? "INTERNATIONAL" : "INDIA",
                null
        ));

        FormulationResponse response = responseFor(request, assessment, jurisdiction, ragResponse);

        log.info(
                "formulation_classification_ready requestId={} status={} classification={} confidence={} route={} latencyMs={}",
                requestId,
                response.status(),
                response.classification(),
                response.confidence(),
                response.regulatoryRoute() == null ? null : response.regulatoryRoute().route(),
                Duration.ofNanos(System.nanoTime() - started).toMillis()
        );
        return response;
    }

    private FormulationResponse responseFor(
            FormulationRequest request,
            FormulationRuleAssessment assessment,
            String jurisdiction,
            RagAskResponse ragResponse
    ) {
        List<QuestionCitation> citations = mapCitations(ragResponse.citations());
        List<QuestionSource> sources = mapSources(ragResponse.sources());
        double confidence = confidenceFor(assessment, ragResponse);

        if (Boolean.TRUE.equals(ragResponse.abstained())) {
            return new FormulationResponse(
                    null,
                    Math.min(confidence, ragResponse.confidence()),
                    true,
                    clarificationService.questionsFor(assessment),
                    "The available RAG evidence was insufficient, so no formulation classification is suggested.",
                    FormulationStatus.INSUFFICIENT_EVIDENCE,
                    null,
                    citations,
                    sources
            );
        }

        if (shouldClarify(assessment, confidence)) {
            return new FormulationResponse(
                    null,
                    confidence,
                    true,
                    clarificationService.questionsFor(assessment),
                    clarificationReason(assessment),
                    FormulationStatus.NEEDS_CLARIFICATION,
                    null,
                    citations,
                    sources
            );
        }

        FormulationClassification classification = assessment.leadingClassification();
        RegulatoryRoute route = routeService.routeFor(classification, jurisdiction);
        return new FormulationResponse(
                classification,
                confidence,
                false,
                List.of(),
                "Based on the structured information provided and the available authoritative knowledge sources, the product appears most consistent with "
                        + classification + ". This is a routing suggestion, not a final legal determination.",
                FormulationStatus.CLASSIFIED,
                route,
                citations,
                sources
        );
    }

    private boolean shouldClarify(FormulationRuleAssessment assessment, double confidence) {
        return assessment.leadingScore() < 2
                || assessment.leadingScore() == assessment.secondScore()
                || assessment.hasConflict()
                || confidence < 0.70;
    }

    private String clarificationReason(FormulationRuleAssessment assessment) {
        if (assessment.hasConflict()) {
            return "The provided information contains conflicting classification signals: " + String.join(" ", assessment.conflicts());
        }
        if (assessment.leadingScore() < 2) {
            return "The provided information does not contain enough classification signals to suggest a category safely.";
        }
        return "The provided information does not distinguish the intended regulatory positioning clearly enough.";
    }

    private double confidenceFor(FormulationRuleAssessment assessment, RagAskResponse ragResponse) {
        double score = 0.25;
        score += Math.min(assessment.leadingScore(), 4) * 0.14;
        score += Math.max(0, assessment.leadingScore() - assessment.secondScore()) * 0.06;
        score += Boolean.TRUE.equals(ragResponse.abstained()) ? 0.0 : Math.min(ragResponse.confidence(), 1.0) * 0.18;
        score -= assessment.conflicts().size() * 0.18;
        score -= assessment.missingInformation().size() * 0.06;
        return Math.round(Math.max(0.0, Math.min(1.0, score)) * 10000.0) / 10000.0;
    }

    private String formulationJurisdiction(FormulationRequest request) {
        String market = (nullToEmpty(request.targetMarket()) + " " + nullToEmpty(request.country())).toLowerCase(Locale.ROOT);
        if (market.contains("international") || market.contains("global") || market.contains("wipo")
                || market.contains("usa") || market.contains("europe") || market.contains("eu")) {
            return "INTERNATIONAL";
        }
        return "INDIA";
    }

    private String ragQueryFor(FormulationRequest request, FormulationRuleAssessment assessment) {
        return "Determine authoritative regulatory context for an Ayurvedic formulation classification. "
                + "Potential rule signal: " + assessment.leadingClassification() + ". "
                + "Dosage form: " + nullToEmpty(request.dosageForm()) + ". "
                + "Intended use: " + nullToEmpty(request.intendedUse()) + ". "
                + "Claims summary: " + String.join("; ", request.claims()) + ". "
                + "Classical reference provided: " + (request.classicalReference() == null ? "no" : "yes") + ". "
                + "Traditional use indicated: " + request.traditionalUse() + ". "
                + "Target market: " + nullToEmpty(request.targetMarket()) + ".";
    }

    private List<QuestionCitation> mapCitations(List<RagCitation> citations) {
        return citations.stream()
                .map(citation -> new QuestionCitation(
                        citation.document(),
                        citation.documentId(),
                        citation.page(),
                        citation.section(),
                        citation.authority(),
                        citation.sourceUrl(),
                        citation.chunkId()
                ))
                .toList();
    }

    private List<QuestionSource> mapSources(List<RagSource> sources) {
        return sources.stream()
                .map(source -> new QuestionSource(source.documentId(), source.score()))
                .toList();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
