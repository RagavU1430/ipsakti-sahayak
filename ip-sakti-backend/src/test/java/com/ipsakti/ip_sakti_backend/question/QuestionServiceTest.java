package com.ipsakti.ip_sakti_backend.question;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

import com.ipsakti.ip_sakti_backend.multilingual.TranslationProvider;
import com.ipsakti.ip_sakti_backend.multilingual.TranslationService;
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
import com.ipsakti.ip_sakti_backend.question.general.GeneralLlmProvider;
import com.ipsakti.ip_sakti_backend.question.routing.DefaultQueryRouter;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class QuestionServiceTest {

    private RagClient ragClient;
    private TranslationProvider translationProvider;
    private QuestionService questionService;
    private GeneralLlmProvider generalLlmProvider;

    @BeforeEach
    void setUp() {
        ragClient = Mockito.mock(RagClient.class);
        translationProvider = Mockito.mock(TranslationProvider.class);
        generalLlmProvider = Mockito.mock(GeneralLlmProvider.class);
        when(generalLlmProvider.providerName()).thenReturn("test-general");
        questionService = new QuestionService(
                ragClient,
                new QuestionIntentClassifier(),
                new JurisdictionResolver(),
                new TranslationService(translationProvider),
                new DefaultQueryRouter(),
                generalLlmProvider
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
    void ordinaryQuestionUsesGeneralProviderWithoutRag() {
        when(ragClient.ask(any())).thenReturn(new RagAskResponse(
                "I could not find sufficient authoritative evidence.",
                0.18,
                true,
                List.of(),
                List.of()
        ));

        when(generalLlmProvider.answer("What is the capital of Mars?")).thenReturn("Mars has no capital city.");
        QuestionResponse response = questionService.answer(new QuestionRequest(
                "What is the capital of Mars?",
                Jurisdiction.AUTO,
                Language.EN
        ));

        assertThat(response.answerType()).isEqualTo(AnswerType.GENERAL_FALLBACK);
        assertThat(response.answer()).isEqualTo("Mars has no capital city.");
        assertThat(response.confidence()).isNull();
        assertThat(response.abstained()).isFalse();
        assertThat(response.language()).isEqualTo(Language.EN);
        assertThat(response.citations()).isEmpty();
        assertThat(response.sources()).isEmpty();
        verify(ragClient, never()).ask(any());
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

        when(generalLlmProvider.answer("What is machine learning?")).thenReturn("General answer");
        QuestionResponse response = questionService.answer(new QuestionRequest(
                "What is machine learning?",
                Jurisdiction.AUTO,
                Language.EN
        ));

        assertThat(response.answerType()).isEqualTo(AnswerType.GENERAL_FALLBACK);
        assertThat(response.intent()).isEqualTo(QuestionIntent.GENERAL);
        assertThat(response.language()).isEqualTo(Language.EN);
        assertThat(response.confidence()).isNull();
        verify(ragClient, never()).ask(any());
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

    @Test
    void translatesTamilQuestionToCanonicalRagAndTranslatesAnswerBack() {
        when(translationProvider.translate(eq("இந்தியாவில் வர்த்தக முத்திரையை பதிவு செய்ய என்ன தேவைகள்?"), eq(Language.TA), eq(Language.EN)))
                .thenReturn("What are the requirements for registering a trademark in India?");
        when(translationProvider.translate(eq("Grounded answer"), eq(Language.EN), eq(Language.TA)))
                .thenReturn("மொழிபெயர்க்கப்பட்ட பதில்");
        when(ragClient.ask(any())).thenReturn(new RagAskResponse(
                "Grounded answer",
                0.94,
                false,
                List.of(new RagCitation("Trade Marks Act, 1999", "IND-TM-ACT-1999", 12, "Section 18",
                        "Government of India", "https://example.invalid", "chunk-1")),
                List.of(new RagSource("IND-TM-ACT-1999", 0.95))
        ));

        QuestionResponse response = questionService.answer(new QuestionRequest(
                "இந்தியாவில் வர்த்தக முத்திரையை பதிவு செய்ய என்ன தேவைகள்?",
                Jurisdiction.INDIA,
                Language.TA
        ));

        assertThat(response.answer()).isEqualTo("மொழிபெயர்க்கப்பட்ட பதில்");
        assertThat(response.language()).isEqualTo(Language.TA);
        assertThat(response.detectedLanguage()).isEqualTo(Language.TA);
        assertThat(response.processingLanguage()).isEqualTo(Language.EN);
        assertThat(response.confidence()).isEqualTo(0.94);
        assertThat(response.citations().getFirst().documentId()).isEqualTo("IND-TM-ACT-1999");

        ArgumentCaptor<RagAskRequest> captor = ArgumentCaptor.forClass(RagAskRequest.class);
        verify(ragClient).ask(captor.capture());
        assertThat(captor.getValue().question()).isEqualTo("What are the requirements for registering a trademark in India?");
    }

    @Test
    void translatesHindiAbstentionWithoutChangingAbstentionState() {
        when(translationProvider.translate(eq("पेटेंट के बाहर का प्रश्न"), eq(Language.HI), eq(Language.EN)))
                .thenReturn("Question outside patent scope");
        when(translationProvider.translate(eq("I could not find sufficient authoritative evidence."), eq(Language.EN), eq(Language.HI)))
                .thenReturn("पर्याप्त प्रमाण नहीं मिला।");
        when(ragClient.ask(any())).thenReturn(new RagAskResponse(
                "I could not find sufficient authoritative evidence.",
                0.18,
                true,
                List.of(),
                List.of()
        ));

        QuestionResponse response = questionService.answer(new QuestionRequest(
                "What is machine learning?",
                Jurisdiction.AUTO,
                Language.EN
        ));

        assertThat(response.answerType()).isEqualTo(AnswerType.GENERAL_FALLBACK);
        assertThat(response.intent()).isEqualTo(QuestionIntent.GENERAL);
        assertThat(response.language()).isEqualTo(Language.EN);
        assertThat(response.confidence()).isNull();
        verify(ragClient, never()).ask(any());
    }


    @Test
    void test_guardrail_upgrades_general_to_domain_rag() {
        when(generalLlmProvider.answer("What is statutory legal protection under section 377?"))
                .thenReturn("This question requires authoritative domain evidence and must be routed to RAG.");
        when(ragClient.ask(any())).thenReturn(new RagAskResponse(
                "Section 377 legal definition and authoritative details",
                0.88,
                false,
                List.of(new RagCitation("IPC Act", "IND-IPC-377", 4, "Section 377", "Supreme Court of India", "https://example.invalid/377", "chunk-377")),
                List.of(new RagSource("IND-IPC-377", 0.90))
        ));

        QuestionResponse response = questionService.answer(new QuestionRequest(
                "What is statutory legal protection under section 377?",
                Jurisdiction.INDIA,
                Language.EN
        ));

        assertThat(response.route()).isEqualTo("DOMAIN_RAG");
        assertThat(response.answer()).isEqualTo("Section 377 legal definition and authoritative details");
        assertThat(response.confidence()).isEqualTo(0.88);
        assertThat(response.citations()).hasSize(1);
        assertThat(response.citations().getFirst().documentId()).isEqualTo("IND-IPC-377");
        verify(ragClient).ask(any());
    }

    @Test
    void testSection377RoutesToDomainRag() {
        when(ragClient.ask(any())).thenReturn(new RagAskResponse(
                "Section 377 answer grounded in evidence",
                0.92,
                false,
                List.of(new RagCitation("Statute Document", "DOC-377", 1, "Section 377", "Authority", "https://example.invalid", "chunk-1")),
                List.of(new RagSource("DOC-377", 0.93))
        ));

        QuestionResponse response = questionService.answer(new QuestionRequest(
                "What is section 377 in India?",
                Jurisdiction.INDIA,
                Language.EN
        ));

        assertThat(response.route()).isEqualTo("DOMAIN_RAG");
        assertThat(response.answer()).isEqualTo("Section 377 answer grounded in evidence");
        verify(ragClient).ask(any());
    }

    @Test
    void testSection3pAndSpokenVariants() {
        when(ragClient.ask(any())).thenReturn(new RagAskResponse(
                "Section 3(p) excludes traditional knowledge from patentability.",
                0.96,
                false,
                List.of(new RagCitation("Patents Act, 1970", "IND-PAT-ACT-1970", 3, "Section 3(p)", "IPO", "https://example.invalid", "chunk-3p")),
                List.of(new RagSource("IND-PAT-ACT-1970", 0.97))
        ));

        List<String> variants = List.of(
                "What is Section 3(p) of the Indian Patents Act?",
                "What is Section 3P?",
                "What is section three p?",
                "What is section 3 p?"
        );

        for (String q : variants) {
            QuestionResponse response = questionService.answer(new QuestionRequest(q, Jurisdiction.INDIA, Language.EN));
            assertThat(response.route()).isEqualTo("DOMAIN_RAG");
            assertThat(response.answer()).contains("Section 3(p)");
        }
    }

    @Test
    void testNegativeGeneralQuestionsRemainGeneral() {
        when(generalLlmProvider.answer(any())).thenReturn("General answer");

        List<String> generalQuestions = List.of(
                "Hi",
                "Hello",
                "How are you?",
                "What is Python?",
                "Explain machine learning",
                "What is an API?"
        );

        for (String q : generalQuestions) {
            QuestionResponse response = questionService.answer(new QuestionRequest(q, Jurisdiction.AUTO, Language.EN));
            assertThat(response.route()).isEqualTo("GENERAL");
            assertThat(response.answerType()).isEqualTo(AnswerType.GENERAL_FALLBACK);
            verify(ragClient, never()).ask(any());
        }
    }
}
