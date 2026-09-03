package com.ipsakti.ip_sakti_backend.tk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.ipsakti.ip_sakti_backend.multilingual.TranslationProvider;
import com.ipsakti.ip_sakti_backend.multilingual.TranslationService;
import com.ipsakti.ip_sakti_backend.question.model.Language;
import com.ipsakti.ip_sakti_backend.rag.RagClient;
import com.ipsakti.ip_sakti_backend.rag.dto.RagAskResponse;
import com.ipsakti.ip_sakti_backend.rag.dto.RagCitation;
import com.ipsakti.ip_sakti_backend.rag.dto.RagSource;
import com.ipsakti.ip_sakti_backend.tk.analysis.TkEvidenceAnalyzer;
import com.ipsakti.ip_sakti_backend.tk.analysis.TkQueryAnalyzer;
import com.ipsakti.ip_sakti_backend.tk.model.TkOverlapClassification;
import com.ipsakti.ip_sakti_backend.tk.model.TkOverlapRequest;
import com.ipsakti.ip_sakti_backend.tk.model.TkOverlapResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class TkMultilingualRegressionTest {

    @Test
    void thirtyMultilingualTkRegressionCasesPreserveEvidenceAndSafetyContract() {
        for (Language language : Language.values()) {
            for (Case testCase : cases()) {
                RagClient ragClient = Mockito.mock(RagClient.class);
                TranslationProvider provider = Mockito.mock(TranslationProvider.class);
                if (language != Language.EN) {
                    when(provider.translate(any(), eq(language), eq(Language.EN))).thenReturn(testCase.canonicalEnglish());
                    when(provider.translate(any(), eq(Language.EN), eq(language))).thenReturn("translated: " + testCase.expected().name());
                    when(provider.providerName()).thenReturn("gemini-2.0-flash");
                }
                when(ragClient.ask(any())).thenReturn(testCase.outOfCorpus()
                        ? abstention()
                        : testCase.expected() == TkOverlapClassification.POTENTIAL_TK_OVERLAP ? partialEvidence() : strongEvidence());

                TkOverlapService service = new TkOverlapService(
                        ragClient,
                        new TranslationService(provider),
                        new TkQueryAnalyzer(),
                        new TkEvidenceAnalyzer()
                );

                TkOverlapResponse response = service.analyze(new TkOverlapRequest(testCase.userText(language), language));

                assertThat(response.language()).isEqualTo(language);
                assertThat(response.classification()).isEqualTo(testCase.expected());
                assertThat(response.confidence()).isGreaterThanOrEqualTo(testCase.outOfCorpus() ? 0.10 : 0.60);
                assertThat(response.abstained()).isEqualTo(testCase.outOfCorpus());
                if (testCase.outOfCorpus()) {
                    assertThat(response.citations()).isEmpty();
                    assertThat(response.sources()).isEmpty();
                } else {
                    assertThat(response.citations()).hasSizeGreaterThanOrEqualTo(1);
                    assertThat(response.citations().getFirst().documentId()).isEqualTo("IND-PAT-ACT-1970");
                    assertThat(response.citations().getFirst().section()).isEqualTo("Section 3(p)");
                    assertThat(response.sources().getFirst().documentId()).isEqualTo("IND-PAT-ACT-1970");
                    assertThat(response.overlapTypes()).isNotEmpty();
                    assertThat(response.explanation()).doesNotContain("stolen", "guaranteed", "invalid");
                }
            }
        }
    }

    private List<Case> cases() {
        return List.of(
                new Case("patent", "A turmeric extract invention with traditional Ayurvedic medicinal use in India.", false, TkOverlapClassification.STRONG_TK_OVERLAP),
                new Case("trademark", "A neem-based herbal product using traditional knowledge references for an Indian brand.", false, TkOverlapClassification.POTENTIAL_TK_OVERLAP),
                new Case("traditional_knowledge", "A tulsi decoction described as a known community home remedy.", false, TkOverlapClassification.STRONG_TK_OVERLAP),
                new Case("formulation", "A herbal formulation containing amla, ginger, and honey for therapeutic use.", false, TkOverlapClassification.POTENTIAL_TK_OVERLAP),
                new Case("out_of_corpus", "A spacecraft navigation algorithm with no plant, community, biological, or traditional knowledge facts.", true, TkOverlapClassification.INSUFFICIENT_EVIDENCE)
        );
    }

    private RagAskResponse strongEvidence() {
        return new RagAskResponse(
                "Section 3(p) discusses traditional knowledge and known properties of traditionally known components. The retrieved evidence is relevant to Ayurvedic plant formulations, biological resources, and documented knowledge.",
                0.86,
                false,
                List.of(
                        new RagCitation("Patents Act, 1970", "IND-PAT-ACT-1970", 4, "Section 3(p)", "Government of India", "https://example.invalid/patents", "chunk-1"),
                        new RagCitation("Biological Diversity Act, 2002", "IND-BD-ACT-2002", 2, "Section 3", "Government of India", "https://example.invalid/bd", "chunk-2")
                ),
                List.of(new RagSource("IND-PAT-ACT-1970", 0.91), new RagSource("IND-BD-ACT-2002", 0.87))
        );
    }

    private RagAskResponse partialEvidence() {
        return new RagAskResponse(
                "Relevant traditional knowledge evidence was retrieved, but it only partially discusses known plant uses and should be reviewed cautiously.",
                0.62,
                false,
                List.of(new RagCitation("Patents Act, 1970", "IND-PAT-ACT-1970", 4, "Section 3(p)", "Government of India", "https://example.invalid/patents", "chunk-1")),
                List.of(new RagSource("IND-PAT-ACT-1970", 0.69))
        );
    }

    private RagAskResponse abstention() {
        return new RagAskResponse(
                "I could not find sufficient authoritative evidence.",
                0.18,
                true,
                List.of(),
                List.of()
        );
    }

    private record Case(String id, String canonicalEnglish, boolean outOfCorpus, TkOverlapClassification expected) {
        String userText(Language language) {
            return language == Language.EN ? canonicalEnglish : id + " case in " + language.toJson();
        }
    }
}
