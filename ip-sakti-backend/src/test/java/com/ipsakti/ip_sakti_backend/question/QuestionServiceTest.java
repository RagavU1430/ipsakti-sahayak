package com.ipsakti.ip_sakti_backend.question;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ipsakti.ip_sakti_backend.question.classification.JurisdictionResolver;
import com.ipsakti.ip_sakti_backend.question.classification.QuestionIntentClassifier;
import com.ipsakti.ip_sakti_backend.question.model.AnswerType;
import com.ipsakti.ip_sakti_backend.question.model.Jurisdiction;
import com.ipsakti.ip_sakti_backend.question.model.Language;
import com.ipsakti.ip_sakti_backend.question.model.QuestionIntent;
import com.ipsakti.ip_sakti_backend.question.model.QuestionRequest;
import com.ipsakti.ip_sakti_backend.question.model.QuestionResponse;
import com.ipsakti.ip_sakti_backend.rag.RagClient;
import com.ipsakti.ip_sakti_backend.rag.dto.RagAskRequest;
import com.ipsakti.ip_sakti_backend.rag.dto.RagAskResponse;
import com.ipsakti.ip_sakti_backend.rag.dto.RagCitation;
import com.ipsakti.ip_sakti_backend.rag.dto.RagSource;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class QuestionServiceTest {

    private RagClient ragClient;
    private QuestionService questionService;

    @BeforeEach
    void setUp() {
        ragClient = Mockito.mock(RagClient.class);
        questionService = new QuestionService(
                ragClient,
                new QuestionIntentClassifier(),
                new JurisdictionResolver()
        );
    }

    @Test
    void mapsGroundedRagResponseAndPreservesEvidenceMetadata() {
        when(ragClient.ask(any())).thenReturn(new RagAskResponse(
                "Grounded answer",
                0.94,
                false,
                List.of(new RagCitation(
                        "Patents Act, 1970",
                        "IND-PAT-ACT-1970",
                        12,
                        "Section 3",
                        "Parliament of India",
                        "https://example.invalid/patents",
                        "chunk-1"
                )),
                List.of(new RagSource("IND-PAT-ACT-1970", 0.95))
        ));

        QuestionResponse response = questionService.answer(new QuestionRequest(
                "Can a classical Ayurvedic formulation be patented?",
                Jurisdiction.INDIA,
                Language.EN
        ));

        assertThat(response.answerType()).isEqualTo(AnswerType.RAG_GROUNDED);
        assertThat(response.confidence()).isEqualTo(0.94);
        assertThat(response.abstained()).isFalse();
        assertThat(response.jurisdiction()).isEqualTo(Jurisdiction.INDIA);
        assertThat(response.language()).isEqualTo(Language.EN);
        assertThat(response.intent()).isEqualTo(QuestionIntent.PATENT);
        assertThat(response.citations()).hasSize(1);
        assertThat(response.citations().getFirst().documentId()).isEqualTo("IND-PAT-ACT-1970");
        assertThat(response.sources().getFirst().score()).isEqualTo(0.95);

        ArgumentCaptor<RagAskRequest> captor = ArgumentCaptor.forClass(RagAskRequest.class);
        verify(ragClient).ask(captor.capture());
        assertThat(captor.getValue().domain()).isEqualTo("PATENT");
        assertThat(captor.getValue().jurisdiction()).isEqualTo("INDIA");
    }

    @Test
    void preservesAbstentionWithoutReplacingAnswerOrConfidence() {
        when(ragClient.ask(any())).thenReturn(new RagAskResponse(
                "I could not find sufficient authoritative evidence.",
                0.18,
                true,
                List.of(),
                List.of()
        ));

        QuestionResponse response = questionService.answer(new QuestionRequest(
                "What is the capital of Mars?",
                Jurisdiction.AUTO,
                Language.HI
        ));

        assertThat(response.answerType()).isEqualTo(AnswerType.ABSTAINED);
        assertThat(response.answer()).isEqualTo("I could not find sufficient authoritative evidence.");
        assertThat(response.confidence()).isEqualTo(0.18);
        assertThat(response.abstained()).isTrue();
        assertThat(response.language()).isEqualTo(Language.HI);
        assertThat(response.citations()).isEmpty();
        assertThat(response.sources()).isEmpty();
    }

    @Test
    void preservesGeneralFallbackClassificationFromRagShape() {
        when(ragClient.ask(any())).thenReturn(new RagAskResponse(
                "General answer",
                0.42,
                false,
                List.of(),
                List.of()
        ));

        QuestionResponse response = questionService.answer(new QuestionRequest(
                "What is machine learning?",
                Jurisdiction.AUTO,
                Language.TA
        ));

        assertThat(response.answerType()).isEqualTo(AnswerType.GENERAL_FALLBACK);
        assertThat(response.intent()).isEqualTo(QuestionIntent.GENERAL);
        assertThat(response.language()).isEqualTo(Language.TA);
    }

    @Test
    void autoJurisdictionUsesExplicitInternationalSignalsConservatively() {
        when(ragClient.ask(any())).thenReturn(new RagAskResponse("Answer", 0.9, false, List.of(), List.of()));

        QuestionResponse response = questionService.answer(new QuestionRequest(
                "What is the WIPO GRATK Treaty?",
                Jurisdiction.AUTO,
                Language.EN
        ));

        ArgumentCaptor<RagAskRequest> captor = ArgumentCaptor.forClass(RagAskRequest.class);
        verify(ragClient).ask(captor.capture());
        assertThat(response.intent()).isEqualTo(QuestionIntent.INTERNATIONAL_IP);
        assertThat(response.jurisdiction()).isEqualTo(Jurisdiction.INTERNATIONAL);
        assertThat(captor.getValue().domain()).isEqualTo("INTERNATIONAL");
        assertThat(captor.getValue().jurisdiction()).isEqualTo("INTERNATIONAL");
    }
}
