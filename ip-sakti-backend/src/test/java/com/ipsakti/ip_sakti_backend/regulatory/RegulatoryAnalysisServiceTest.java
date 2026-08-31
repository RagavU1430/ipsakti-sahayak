package com.ipsakti.ip_sakti_backend.regulatory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.ipsakti.ip_sakti_backend.question.model.Jurisdiction;
import com.ipsakti.ip_sakti_backend.rag.RagClient;
import com.ipsakti.ip_sakti_backend.rag.dto.RagAskResponse;
import com.ipsakti.ip_sakti_backend.rag.dto.RagCitation;
import com.ipsakti.ip_sakti_backend.rag.dto.RagSource;
import com.ipsakti.ip_sakti_backend.regulatory.engine.AbsAnalysisService;
import com.ipsakti.ip_sakti_backend.regulatory.engine.GratkAnalysisService;
import com.ipsakti.ip_sakti_backend.regulatory.engine.RegulatoryEvidenceMapper;
import com.ipsakti.ip_sakti_backend.regulatory.engine.RegulatoryJurisdictionRouter;
import com.ipsakti.ip_sakti_backend.regulatory.engine.Section3eAnalysisService;
import com.ipsakti.ip_sakti_backend.regulatory.engine.Section3pAnalysisService;
import com.ipsakti.ip_sakti_backend.regulatory.model.GratkResourceType;
import com.ipsakti.ip_sakti_backend.regulatory.model.RegulatoryAnalysisRequest;
import com.ipsakti.ip_sakti_backend.regulatory.model.RegulatoryAnalysisResponse;
import com.ipsakti.ip_sakti_backend.regulatory.model.RegulatoryEngine;
import com.ipsakti.ip_sakti_backend.regulatory.model.RegulatoryEngineResult;
import com.ipsakti.ip_sakti_backend.regulatory.model.RegulatoryStatus;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class RegulatoryAnalysisServiceTest {

    private RagClient ragClient;
    private RegulatoryAnalysisService service;

    @BeforeEach
    void setUp() {
        ragClient = Mockito.mock(RagClient.class);
        RegulatoryEvidenceMapper mapper = new RegulatoryEvidenceMapper();
        RegulatoryJurisdictionRouter router = new RegulatoryJurisdictionRouter();
        service = new RegulatoryAnalysisService(
                router,
                new Section3pAnalysisService(ragClient, mapper, router),
                new Section3eAnalysisService(ragClient, router),
                new AbsAnalysisService(ragClient, mapper, router),
                new GratkAnalysisService(ragClient, mapper, router)
        );
        when(ragClient.ask(any())).thenReturn(grounded());
    }

    @Test
    void traditionalKnowledgeTriggersSection3pReview() {
        RegulatoryAnalysisResponse response = service.analyze(request(true, false, true, Jurisdiction.INDIA));

        RegulatoryEngineResult result = engine(response, RegulatoryEngine.SECTION_3P);
        assertThat(result.status()).isEqualTo(RegulatoryStatus.REVIEW_RECOMMENDED);
        assertThat(result.reason()).contains("Section 3(p)");
        assertThat(result.citations()).hasSize(1);
    }

    @Test
    void unrelatedFormulationIsNotIndicatedForSection3p() {
        RegulatoryAnalysisResponse response = service.analyze(request(false, false, false, Jurisdiction.INDIA));

        assertThat(engine(response, RegulatoryEngine.SECTION_3P).status()).isEqualTo(RegulatoryStatus.NOT_INDICATED);
    }

    @Test
    void ragAbstentionProducesInsufficientEvidence() {
        when(ragClient.ask(any())).thenReturn(new RagAskResponse("No evidence", 0.18, true, List.of(), List.of()));

        RegulatoryAnalysisResponse response = service.analyze(request(true, true, true, Jurisdiction.INDIA));

        assertThat(response.overallStatus()).isEqualTo(RegulatoryStatus.INSUFFICIENT_EVIDENCE);
        assertThat(response.needsClarification()).isTrue();
    }

    @Test
    void conflictingTraditionalKnowledgeSignalsTriggerClarification() {
        RegulatoryAnalysisRequest request = new RegulatoryAnalysisRequest(
                "Classical Product", List.of("Herb"), "tablet", "traditional use", List.of("patent claim"),
                false, "Charaka Samhita", true, "India", "India", Jurisdiction.INDIA,
                true, true, false, true
        );

        RegulatoryAnalysisResponse response = service.analyze(request);

        assertThat(response.needsClarification()).isTrue();
        assertThat(engine(response, RegulatoryEngine.SECTION_3P).reason()).contains("Conflicting");
    }

    @Test
    void knownIngredientsAndMixtureTriggerSection3eReview() {
        RegulatoryAnalysisRequest request = new RegulatoryAnalysisRequest(
                "Known Herbal Mixture", List.of("Herb A", "Herb B"), "tablet", "digestive health", List.of("known ingredients combination"),
                false, null, false, null, "India", Jurisdiction.INDIA,
                false, true, false, false
        );

        RegulatoryAnalysisResponse response = service.analyze(request);

        assertThat(engine(response, RegulatoryEngine.SECTION_3E).status()).isEqualTo(RegulatoryStatus.REVIEW_RECOMMENDED);
    }

    @Test
    void synergisticEffectConflictLowersConfidence() {
        RegulatoryAnalysisRequest request = new RegulatoryAnalysisRequest(
                "Synergy Mixture", List.of("Herb A", "Herb B"), "tablet", "therapeutic", List.of("known ingredients combination"),
                false, null, false, null, "India", Jurisdiction.INDIA,
                true, true, true, false
        );

        RegulatoryAnalysisResponse response = service.analyze(request);

        RegulatoryEngineResult section3e = engine(response, RegulatoryEngine.SECTION_3E);
        assertThat(section3e.reason()).contains("Conflicting");
        assertThat(section3e.confidence()).isLessThan(0.9);
    }

    @Test
    void biologicalResourceIndianContextTriggersAbs() {
        RegulatoryAnalysisResponse response = service.analyze(request(false, true, false, Jurisdiction.INDIA));

        assertThat(engine(response, RegulatoryEngine.ABS).status()).isEqualTo(RegulatoryStatus.POTENTIALLY_APPLICABLE);
    }

    @Test
    void missingOriginContextRequestsClarificationForAbs() {
        RegulatoryAnalysisRequest request = new RegulatoryAnalysisRequest(
                "Bio Product", List.of("Plant"), null, "commercial use", List.of(),
                false, null, true, null, "India", Jurisdiction.INDIA,
                false, false, false, true
        );

        RegulatoryAnalysisResponse response = service.analyze(request);

        assertThat(response.needsClarification()).isTrue();
        assertThat(engine(response, RegulatoryEngine.ABS).status()).isEqualTo(RegulatoryStatus.REVIEW_RECOMMENDED);
    }

    @Test
    void gratkDistinguishesGeneticResourceAndTraditionalKnowledge() {
        RegulatoryAnalysisResponse response = service.analyze(request(true, true, true, Jurisdiction.INTERNATIONAL));

        RegulatoryEngineResult gratk = engine(response, RegulatoryEngine.GRATK);
        assertThat(gratk.status()).isEqualTo(RegulatoryStatus.REVIEW_RECOMMENDED);
        assertThat(gratk.resourceType()).isEqualTo(GratkResourceType.GENETIC_RESOURCE_AND_ASSOCIATED_TK);
        assertThat(response.jurisdiction()).isEqualTo(Jurisdiction.INTERNATIONAL);
    }

    @Test
    void autoClearCountryRoutesToIndia() {
        RegulatoryAnalysisResponse response = service.analyze(request(true, true, true, Jurisdiction.AUTO));

        assertThat(response.jurisdiction()).isEqualTo(Jurisdiction.INDIA);
    }

    @Test
    void autoAmbiguousCountryAsksClarificationWithoutRunningEngines() {
        RegulatoryAnalysisRequest request = new RegulatoryAnalysisRequest(
                "Herbal Product", List.of("Herb"), null, null, List.of(),
                null, null, null, null, null, Jurisdiction.AUTO,
                null, null, null, null
        );

        RegulatoryAnalysisResponse response = service.analyze(request);

        assertThat(response.jurisdiction()).isEqualTo(Jurisdiction.AUTO);
        assertThat(response.needsClarification()).isTrue();
        assertThat(response.engines()).isEmpty();
    }

    @Test
    void confidenceStaysInRange() {
        RegulatoryAnalysisResponse response = service.analyze(request(true, true, true, Jurisdiction.INDIA));

        assertThat(response.overallConfidence()).isBetween(0.0, 1.0);
        assertThat(response.engines()).allMatch(engine -> engine.confidence() >= 0.0 && engine.confidence() <= 1.0);
    }

    private RegulatoryAnalysisRequest request(boolean tk, boolean bio, boolean gr, Jurisdiction jurisdiction) {
        return new RegulatoryAnalysisRequest(
                "Ayurvedic Product",
                List.of("Plant A", "Plant B"),
                "tablet",
                tk ? "traditional knowledge based therapeutic use" : "general wellness use",
                List.of("supports digestive health"),
                tk,
                tk ? "Classical reference" : null,
                bio,
                bio ? "India" : null,
                "India",
                jurisdiction,
                true,
                true,
                false,
                gr
        );
    }

    private RegulatoryEngineResult engine(RegulatoryAnalysisResponse response, RegulatoryEngine engine) {
        return response.engines().stream().filter(result -> result.engine() == engine).findFirst().orElseThrow();
    }

    private RagAskResponse grounded() {
        return new RagAskResponse(
                "Grounded legal evidence",
                0.86,
                false,
                List.of(new RagCitation("Evidence Act", "DOC-1", 1, "Section", "Authority", "https://example.invalid", "chunk-1")),
                List.of(new RagSource("DOC-1", 0.88))
        );
    }
}
