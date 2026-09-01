package com.ipsakti.ip_sakti_backend.formulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.ipsakti.ip_sakti_backend.formulation.classification.FormulationClarificationService;
import com.ipsakti.ip_sakti_backend.formulation.classification.FormulationRuleEngine;
import com.ipsakti.ip_sakti_backend.formulation.classification.RegulatoryRouteService;
import com.ipsakti.ip_sakti_backend.formulation.model.FormulationClassification;
import com.ipsakti.ip_sakti_backend.formulation.model.FormulationRequest;
import com.ipsakti.ip_sakti_backend.formulation.model.FormulationResponse;
import com.ipsakti.ip_sakti_backend.formulation.model.FormulationStatus;
import com.ipsakti.ip_sakti_backend.multilingual.BhashiniClient;
import com.ipsakti.ip_sakti_backend.multilingual.TranslationService;
import com.ipsakti.ip_sakti_backend.question.model.Language;
import com.ipsakti.ip_sakti_backend.rag.RagClient;
import com.ipsakti.ip_sakti_backend.rag.dto.RagAskResponse;
import com.ipsakti.ip_sakti_backend.rag.dto.RagCitation;
import com.ipsakti.ip_sakti_backend.rag.dto.RagSource;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class FormulationClassificationServiceTest {

    private RagClient ragClient;
    private BhashiniClient bhashiniClient;
    private FormulationClassificationService service;

    @BeforeEach
    void setUp() {
        ragClient = Mockito.mock(RagClient.class);
        bhashiniClient = Mockito.mock(BhashiniClient.class);
        service = new FormulationClassificationService(
                ragClient,
                new FormulationRuleEngine(),
                new FormulationClarificationService(),
                new RegulatoryRouteService(),
                new TranslationService(bhashiniClient)
        );
        when(ragClient.ask(any())).thenReturn(groundedRagResponse());
    }

    @Test
    void classifiesClearlyClassicalFormulation() {
        FormulationResponse response = service.classify(new FormulationRequest(
                "Triphala Churna",
                List.of("Haritaki", "Bibhitaki", "Amalaki"),
                "powder",
                "traditional digestive medicine",
                List.of("supports classical therapeutic use"),
                "traditional processing",
                "Charaka Samhita reference",
                true,
                true,
                "India",
                null,
                null,
                null
        ));

        assertThat(response.status()).isEqualTo(FormulationStatus.CLASSIFIED);
        assertThat(response.classification()).isEqualTo(FormulationClassification.CLASSICAL_DRUG);
        assertThat(response.needsClarification()).isFalse();
        assertThat(response.regulatoryRoute().route()).isEqualTo("AYUSH_CLASSICAL_DRUG");
    }

    @Test
    void classifiesClearlyProprietaryFormulation() {
        FormulationResponse response = service.classify(new FormulationRequest(
                "Novel Herbal Tablet",
                List.of("Botanical A", "Botanical B"),
                "tablet",
                "proprietary wellness medicine",
                List.of("new proprietary combination", "ip protection desired"),
                "modified non-classical formulation with new process",
                null,
                false,
                true,
                "India",
                null,
                null,
                null
        ));

        assertThat(response.classification()).isEqualTo(FormulationClassification.PATENT_PROPRIETARY);
        assertThat(response.regulatoryRoute().domains()).contains("AYURVEDA", "PATENT");
    }

    @Test
    void classifiesClearlyAyurvedaAaharNutraceutical() {
        FormulationResponse response = service.classify(new FormulationRequest(
                "Digestive Health Mix",
                List.of("Cumin", "Fennel"),
                "powder",
                "daily food-like digestive support",
                List.of("supports digestion", "nutrition support", "daily consumption"),
                null,
                null,
                false,
                true,
                "India",
                null,
                null,
                null
        ));

        assertThat(response.classification()).isEqualTo(FormulationClassification.AYURVEDA_AAHAR_NUTRACEUTICAL);
        assertThat(response.regulatoryRoute().route()).isEqualTo("AYURVEDA_AAHAR");
    }

    @Test
    void classifiesClearlyCosmetic() {
        FormulationResponse response = service.classify(new FormulationRequest(
                "Herbal Face Cream",
                List.of("Aloe", "Turmeric"),
                "topical cream",
                "skin glow and moisturizing personal care",
                List.of("improves appearance", "beauty glow"),
                null,
                null,
                false,
                true,
                "India",
                null,
                null,
                null
        ));

        assertThat(response.classification()).isEqualTo(FormulationClassification.COSMETIC);
        assertThat(response.regulatoryRoute().route()).isEqualTo("COSMETIC_REGULATORY");
    }

    @Test
    void classifiesPhytopharmaceuticalNewDrugLikeFormulation() {
        FormulationResponse response = service.classify(new FormulationRequest(
                "Standardized Botanical Extract",
                List.of("Plant extract"),
                "capsule",
                "therapeutic drug development",
                List.of("standardized extract", "quantified active constituent", "clinical trial"),
                "active marker standardized botanical extract",
                null,
                false,
                true,
                "India",
                null,
                null,
                null
        ));

        assertThat(response.classification()).isEqualTo(FormulationClassification.PHYTOPHARMACEUTICAL_NEW_DRUG);
        assertThat(response.regulatoryRoute().route()).isEqualTo("PHYTOPHARMACEUTICAL_NEW_DRUG");
    }

    @Test
    void asksClarificationForInsufficientInformation() {
        FormulationResponse response = service.classify(new FormulationRequest(
                "Herbal Product",
                List.of(),
                null,
                null,
                List.of(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        ));

        assertThat(response.status()).isEqualTo(FormulationStatus.NEEDS_CLARIFICATION);
        assertThat(response.classification()).isNull();
        assertThat(response.questions()).isNotEmpty();
    }

    @Test
    void asksClarificationForConflictingTherapeuticAndFoodSignals() {
        FormulationResponse response = service.classify(new FormulationRequest(
                "Diabetes Wellness Beverage",
                List.of("Herb A"),
                "beverage",
                "food product for daily consumption",
                List.of("helps treat diabetes", "nutritional beverage"),
                null,
                null,
                false,
                true,
                "India",
                null,
                null,
                null
        ));

        assertThat(response.status()).isEqualTo(FormulationStatus.NEEDS_CLARIFICATION);
        assertThat(response.classification()).isNull();
        assertThat(response.reason()).contains("conflicting");
    }

    @Test
    void asksClarificationForClassicalReferenceAndModifiedSignals() {
        FormulationResponse response = service.classify(new FormulationRequest(
                "Modified Classical Churna",
                List.of("Herb A"),
                "powder",
                "traditional medicine",
                List.of("modified proprietary classical formulation"),
                "novel process",
                "Sushruta Samhita reference",
                true,
                true,
                "India",
                null,
                null,
                null
        ));

        assertThat(response.status()).isEqualTo(FormulationStatus.NEEDS_CLARIFICATION);
        assertThat(response.questions()).anyMatch(question -> question.contains("identical to a classical formulation"));
    }

    @Test
    void ragAbstentionDoesNotFabricateClassification() {
        when(ragClient.ask(any())).thenReturn(new RagAskResponse(
                "I could not find sufficient authoritative evidence.",
                0.18,
                true,
                List.of(),
                List.of()
        ));

        FormulationResponse response = service.classify(new FormulationRequest(
                "Herbal Face Cream",
                List.of("Aloe"),
                "topical cream",
                "skin beauty cosmetic",
                List.of("glow"),
                null,
                null,
                false,
                true,
                "India",
                null,
                null,
                null
        ));

        assertThat(response.status()).isEqualTo(FormulationStatus.INSUFFICIENT_EVIDENCE);
        assertThat(response.classification()).isNull();
        assertThat(response.needsClarification()).isTrue();
    }

    @Test
    void preservesRagCitationsAndKeepsConfidenceInRange() {
        FormulationResponse response = service.classify(new FormulationRequest(
                "Herbal Face Cream",
                List.of("Aloe"),
                "topical cream",
                "skin beauty cosmetic personal care",
                List.of("glow", "moisturizing"),
                null,
                null,
                false,
                true,
                "India",
                null,
                null,
                null
        ));

        assertThat(response.citations()).hasSize(1);
        assertThat(response.citations().getFirst().documentId()).isEqualTo("IND-AYUSH-TEST");
        assertThat(response.sources().getFirst().score()).isEqualTo(0.91);
        assertThat(response.confidence()).isBetween(0.0, 1.0);
    }

    @Test
    void translatesTamilFormulationInputAndReasonWhilePreservingCategoryAndCitations() {
        when(bhashiniClient.translate(org.mockito.ArgumentMatchers.anyString(), eq(Language.TA), eq(Language.EN)))
                .thenReturn("Herbal Cream skin beauty glow");
        when(bhashiniClient.translate(org.mockito.ArgumentMatchers.contains("COSMETIC"), eq(Language.EN), eq(Language.TA)))
                .thenReturn("தமிழில் வகைப்பாடு காரணம் COSMETIC");

        FormulationResponse response = service.classify(new FormulationRequest(
                "மூலிகை கிரீம்",
                List.of(),
                null,
                "சரும அழகு",
                List.of("பளபளப்பு"),
                null,
                null,
                false,
                true,
                null,
                null,
                null,
                null,
                Language.TA
        ));

        assertThat(response.status()).isEqualTo(FormulationStatus.CLASSIFIED);
        assertThat(response.classification()).isEqualTo(FormulationClassification.COSMETIC);
        assertThat(response.reason()).isEqualTo("தமிழில் வகைப்பாடு காரணம் COSMETIC");
        assertThat(response.language()).isEqualTo(Language.TA);
        assertThat(response.detectedLanguage()).isEqualTo(Language.TA);
        assertThat(response.citations().getFirst().documentId()).isEqualTo("IND-AYUSH-TEST");
    }

    private RagAskResponse groundedRagResponse() {
        return new RagAskResponse(
                "RAG regulatory context",
                0.91,
                false,
                List.of(new RagCitation(
                        "Ayush Evidence",
                        "IND-AYUSH-TEST",
                        10,
                        "Regulatory context",
                        "Test Authority",
                        "https://example.invalid",
                        "chunk-1"
                )),
                List.of(new RagSource("IND-AYUSH-TEST", 0.91))
        );
    }
}
