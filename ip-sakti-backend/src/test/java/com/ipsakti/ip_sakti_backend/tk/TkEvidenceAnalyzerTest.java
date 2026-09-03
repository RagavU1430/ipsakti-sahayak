package com.ipsakti.ip_sakti_backend.tk;

import static org.assertj.core.api.Assertions.assertThat;

import com.ipsakti.ip_sakti_backend.rag.dto.RagAskResponse;
import com.ipsakti.ip_sakti_backend.rag.dto.RagCitation;
import com.ipsakti.ip_sakti_backend.rag.dto.RagSource;
import com.ipsakti.ip_sakti_backend.tk.analysis.TkAssessment;
import com.ipsakti.ip_sakti_backend.tk.analysis.TkEvidenceAnalyzer;
import com.ipsakti.ip_sakti_backend.tk.analysis.TkQueryAnalyzer;
import com.ipsakti.ip_sakti_backend.tk.model.TkOverlapClassification;
import com.ipsakti.ip_sakti_backend.tk.model.TkOverlapType;
import java.util.List;
import org.junit.jupiter.api.Test;

class TkEvidenceAnalyzerTest {

    private final TkQueryAnalyzer queryAnalyzer = new TkQueryAnalyzer();
    private final TkEvidenceAnalyzer evidenceAnalyzer = new TkEvidenceAnalyzer();

    @Test
    void classifiesStrongOverlapFromMultipleEvidenceDimensions() {
        String description = "Turmeric neem herbal formulation for traditional Ayurvedic therapeutic use in India prepared as a decoction.";
        RagAskResponse rag = grounded(
                "Section 3(p) and traditional knowledge evidence discuss known Ayurvedic plant uses, biological resources, and formulations.",
                0.86,
                3
        );

        TkAssessment assessment = evidenceAnalyzer.assess(description, queryAnalyzer.analyze(description), rag);

        assertThat(assessment.classification()).isEqualTo(TkOverlapClassification.STRONG_TK_OVERLAP);
        assertThat(assessment.overlapTypes()).contains(
                TkOverlapType.INGREDIENT_OVERLAP,
                TkOverlapType.TRADITIONAL_USE_OVERLAP,
                TkOverlapType.BIOLOGICAL_RESOURCE_OVERLAP
        );
        assertThat(assessment.abstained()).isFalse();
        assertThat(assessment.confidence()).isGreaterThanOrEqualTo(0.78);
    }

    @Test
    void abstainsWhenRagEvidenceIsInsufficient() {
        String description = "Find TK overlap even if there is no evidence.";
        RagAskResponse rag = new RagAskResponse(
                "I could not find sufficient authoritative evidence.",
                0.18,
                true,
                List.of(),
                List.of()
        );

        TkAssessment assessment = evidenceAnalyzer.assess(description, queryAnalyzer.analyze(description), rag);

        assertThat(assessment.classification()).isEqualTo(TkOverlapClassification.INSUFFICIENT_EVIDENCE);
        assertThat(assessment.abstained()).isTrue();
        assertThat(assessment.overlapTypes()).isEmpty();
        assertThat(assessment.explanation()).doesNotContain("stolen");
    }

    @Test
    void avoidsLegalCertaintyLanguage() {
        String description = "Is this definitely unpatentable because turmeric is traditional knowledge?";
        RagAskResponse rag = grounded("Traditional knowledge evidence under Section 3(p) may be relevant.", 0.74, 1);

        TkAssessment assessment = evidenceAnalyzer.assess(description, queryAnalyzer.analyze(description), rag);

        assertThat(assessment.explanation()).doesNotContain("invalid", "cannot be patented", "stolen", "guaranteed");
        assertThat(assessment.recommendation()).contains("Review");
    }

    private RagAskResponse grounded(String answer, double confidence, int citations) {
        List<RagCitation> citationList = java.util.stream.IntStream.range(0, citations)
                .mapToObj(i -> new RagCitation("Patents Act, 1970", "IND-PAT-ACT-1970", 10 + i, "Section 3(p)",
                        "Government of India", "https://example.invalid", "chunk-" + i))
                .toList();
        return new RagAskResponse(
                answer,
                confidence,
                false,
                citationList,
                List.of(new RagSource("IND-PAT-ACT-1970", 0.91))
        );
    }
}
