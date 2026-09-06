package com.ipsakti.ip_sakti_backend.formulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ipsakti.ip_sakti_backend.formulation.classification.FormulationClarificationService;
import com.ipsakti.ip_sakti_backend.formulation.classification.FormulationRuleEngine;
import com.ipsakti.ip_sakti_backend.formulation.classification.RegulatoryRouteService;
import com.ipsakti.ip_sakti_backend.formulation.model.ClaimCategory;
import com.ipsakti.ip_sakti_backend.formulation.model.DocumentStatus;
import com.ipsakti.ip_sakti_backend.formulation.model.DocumentType;
import com.ipsakti.ip_sakti_backend.formulation.model.FormulationRequest;
import com.ipsakti.ip_sakti_backend.formulation.model.ProductReadinessResponse;
import com.ipsakti.ip_sakti_backend.formulation.model.ProvidedDocument;
import com.ipsakti.ip_sakti_backend.multilingual.LanguageMetadata;
import com.ipsakti.ip_sakti_backend.multilingual.TranslatedText;
import com.ipsakti.ip_sakti_backend.multilingual.TranslationService;
import com.ipsakti.ip_sakti_backend.question.model.Language;
import com.ipsakti.ip_sakti_backend.rag.RagClient;
import com.ipsakti.ip_sakti_backend.rag.dto.RagAskResponse;
import com.ipsakti.ip_sakti_backend.rag.dto.RagCitation;
import com.ipsakti.ip_sakti_backend.rag.dto.RagSource;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AyurvedaProductReadinessServiceTest {

    private RagClient ragClient;
    private TranslationService translationService;
    private AyurvedaProductReadinessService service;

    @BeforeEach
    void setUp() {
        ragClient = mock(RagClient.class);
        translationService = mock(TranslationService.class);

        when(translationService.toCanonical(any(), any(), any())).thenAnswer(invocation -> {
            String text = invocation.getArgument(0);
            Language lang = invocation.getArgument(1);
            return new TranslatedText(text, text, new LanguageMetadata(lang, lang, Language.EN));
        });

        when(translationService.fromCanonical(any(), any(), any())).thenAnswer(invocation -> {
            String text = invocation.getArgument(0);
            LanguageMetadata meta = invocation.getArgument(1);
            return "[TRANSLATED-" + meta.requestedLanguage() + "] " + text;
        });

        when(translationService.fromCanonicalList(any(), any(), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<String> list = (List<String>) invocation.getArgument(0);
            LanguageMetadata meta = invocation.getArgument(1);
            return list == null ? List.of() : list.stream().map(s -> "[TRANSLATED-" + meta.requestedLanguage() + "] " + s).toList();
        });

        when(ragClient.ask(any())).thenReturn(new RagAskResponse(
                "Authoritative legal framework provisions from Drugs and Cosmetics Act and Rules.",
                0.88,
                false,
                List.of(new RagCitation(
                        "Drugs and Cosmetics Act, 1940",
                        "IND-DCA-1940",
                        12,
                        "33-E",
                        "Ministry of AYUSH",
                        "https://example.gov.in/act.pdf",
                        "chunk-dca-1"
                )),
                List.of(new RagSource("IND-DCA-1940", 0.92))
        ));

        service = new AyurvedaProductReadinessService(
                ragClient,
                new FormulationRuleEngine(),
                new FormulationClarificationService(),
                new RegulatoryRouteService(),
                translationService
        );
    }

    @Test
    void testA_classicalAyurvedaFormulation() {
        FormulationRequest request = new FormulationRequest(
                "Triphala Guggulu Tablets",
                List.of("Haritaki (Terminalia chebula)", "Bibhitaki (Terminalia bellirica)", "Amalaki (Emblica officinalis)", "Shuddha Guggulu"),
                "Tablet",
                "Traditional joint comfort and digestive support",
                List.of("Traditional digestive balance as per Ayurvedic Formulary of India"),
                "Classical Ayurvedic Bhavana and Vati preparation method",
                "Sharangadhara Samhita, Madhyama Khanda",
                true,
                true,
                "India",
                "India",
                "AYUSH/DCA/2021/104",
                "Classical Ayurvedic Medicine",
                Language.EN,
                List.of("1:1:1:3"),
                "Cultivated in Madhya Pradesh, India",
                "Dabur India Ltd",
                "India",
                List.of(
                        new ProvidedDocument("doc-1", "Formulation Specification", DocumentType.FORMULATION_SPECIFICATION, DocumentStatus.DOCUMENT_PROVIDED, "Classical ratios"),
                        new ProvidedDocument("doc-2", "Text Reference", DocumentType.CLASSICAL_TEXT_REFERENCE, DocumentStatus.DOCUMENT_PROVIDED, "Sharangadhara Samhita citation")
                )
        );

        ProductReadinessResponse res = service.analyze(request);

        assertThat(res.classification().get("category")).isEqualTo("CLASSICAL_AYURVEDIC_FORMULATION");
        assertThat(res.regulatory().get("pathway")).isEqualTo("AYUSH_CLASSICAL_DRUG");
        assertThat(res.traditionalKnowledge().get("status")).isEqualTo("CONFIRMED");
        assertThat(res.traditionalKnowledge().get("isClassicalFormulation")).isEqualTo(true);
        assertThat(res.scores().overall()).isNotEqualTo("INSUFFICIENT_EVIDENCE");
        assertThat(res.report()).contains("Section 3(p)");
        assertThat(res.report()).doesNotContain("Approved for sale");
        assertThat(res.report()).doesNotContain("Market approved");
    }

    @Test
    void testB_proprietaryAyurvedaFormulation() {
        FormulationRequest request = new FormulationRequest(
                "CurcuNano Joint Pain Gel",
                List.of("Curcumin nanoparticle extract", "Piperine bioavailability enhancer", "Boswellia resin standardized"),
                "Topical Gel",
                "Targeted joint pain management",
                List.of("Enhanced bioavailability lipid nanoparticle delivery matrix for anti-inflammatory support"),
                "Novel nano-emulsion and liposomal entrapment technique",
                null,
                false,
                true,
                "India",
                "India",
                null,
                "Proprietary",
                Language.EN,
                List.of("250mg", "10mg", "100mg"),
                "Standardized extracts from Kerala, India",
                "BioAyur Labs",
                "India",
                List.of()
        );

        ProductReadinessResponse res = service.analyze(request);

        assertThat(res.classification().get("category")).isEqualTo("AYURVEDIC_PROPRIETARY_MEDICINE");
        assertThat(res.traditionalKnowledge().get("isClassicalFormulation")).isEqualTo(false);
        assertThat(res.biodiversityAbs().get("status")).isEqualTo("POTENTIALLY_RELEVANT");
        assertThat(res.report()).contains("Section 6 of the Biological Diversity Act");
    }

    @Test
    void testC_herbalFoodNutraceutical() {
        FormulationRequest request = new FormulationRequest(
                "AyurVital Daily Infusion Tea",
                List.of("Tulsi leaves", "Ginger rhizome", "Cardamom seeds"),
                "Herbal Tea / Infusion",
                "Daily nutritional health drink and wellness beverage",
                List.of("Nutritional dietary supplement for daily wellness and refreshment"),
                "Coarse blend and sachet packaging",
                null,
                true,
                true,
                "India",
                "India",
                null,
                "Ayurveda Aahara",
                Language.EN,
                List.of("40%", "30%", "30%"),
                "Assam, India",
                "TeaCo",
                "India",
                List.of()
        );

        ProductReadinessResponse res = service.analyze(request);

        assertThat(res.classification().get("category")).isEqualTo("FOOD_OR_NUTRACEUTICAL");
        assertThat(res.regulatory().get("pathway")).isEqualTo("AYURVEDA_AAHAR");
        assertThat(res.regulatory().get("governingFramework")).asString().contains("Ayurveda Aahara");
    }

    @Test
    void testD_cosmeticHerbalProduct() {
        FormulationRequest request = new FormulationRequest(
                "GlowKanti Herbal Face Cream",
                List.of("Kumkumadi oil", "Aloe vera gel", "Saffron extract"),
                "Face Cream",
                "Topical skin moisturizing, complexion radiance, and beautifying appearance",
                List.of("Improves skin glow, radiance, and facial appearance"),
                "Cold emulsion blending",
                null,
                false,
                true,
                "India",
                "India",
                null,
                "Cosmetic",
                Language.EN,
                List.of(),
                "India",
                "BeautyHerbs",
                "India",
                List.of()
        );

        ProductReadinessResponse res = service.analyze(request);

        assertThat(res.classification().get("category")).isEqualTo("COSMETIC_OR_PERSONAL_CARE");
        assertThat(res.regulatory().get("pathway")).isEqualTo("COSMETIC_REGULATORY");
    }

    @Test
    void testE_productWithProhibitedDiseaseClaim() {
        FormulationRequest request = new FormulationRequest(
                "CancerCure Rasayana",
                List.of("Ashwagandha", "Curcumin"),
                "Syrup",
                "Complete cure for cancer and prevents diabetes",
                List.of("Cures cancer and prevents diabetes mellitus within 30 days"),
                null,
                null,
                true,
                true,
                "India",
                "India",
                null,
                null,
                Language.EN,
                List.of(),
                "India",
                "MiracleAyur",
                "India",
                List.of()
        );

        ProductReadinessResponse res = service.analyze(request);

        assertThat(res.scores().claims()).isEqualTo("HIGH_REVIEW_REQUIRED");
        assertThat(res.scores().overall()).isEqualTo("REQUIRES_REGULATORY_REVIEW");
        assertThat(res.claims().getFirst().category()).isEqualTo(ClaimCategory.DISEASE_CLAIM);
        assertThat(res.claims().getFirst().regulatoryImplication()).contains("Drugs and Magic Remedies");
    }

    @Test
    void testF_missingDocumentsScenario() {
        FormulationRequest request = new FormulationRequest(
                "Unknown Herb Extract Blend",
                List.of("Rare Mountain Plant"),
                null,
                null,
                List.of(),
                null,
                null,
                false,
                true,
                "India",
                "India",
                null,
                null,
                Language.EN,
                List.of(),
                null,
                null,
                null,
                List.of()
        );

        ProductReadinessResponse res = service.analyze(request);

        assertThat(res.scores().documentCompleteness()).isIn("PARTIAL", "INSUFFICIENT_EVIDENCE");
        assertThat(res.gaps()).isNotEmpty();
        assertThat(res.gaps().stream().anyMatch(g -> g.documentArea().contains("Specification") && g.status().equals("MISSING"))).isTrue();
    }

    @Test
    void testG_negativeSafetyGuarantees() {
        FormulationRequest request = new FormulationRequest(
                "Standard Herbal Powder",
                List.of("Neem leaves"),
                "Powder",
                "General wellness",
                List.of("Supports health"),
                null,
                null,
                true,
                true,
                "India",
                "India",
                null,
                null,
                Language.EN,
                List.of(),
                "India",
                "GoodLife",
                "India",
                List.of()
        );

        ProductReadinessResponse res = service.analyze(request);

        // Verify the response never guarantees outcomes or certifies legality
        String report = res.report().toLowerCase();
        assertThat(report).doesNotContain("approved for sale");
        assertThat(report).doesNotContain("legally certified");
        assertThat(report).doesNotContain("patent guaranteed");
        assertThat(report).doesNotContain("safe for sale");
        assertThat(report).doesNotContain("clinically proven");
        assertThat(res.status()).isNotEqualTo("APPROVED");
    }

    @Test
    void testH_multilingualTamilFormulationAnalysis() {
        FormulationRequest request = new FormulationRequest(
                "திரிபலா மாத்திரை",
                List.of("கடுக்காய்", "நெல்லிக்காய்", "தான்றிக்காய்"),
                "மாத்திரை",
                "பாரம்பரிய செரிமான சமநிலை",
                List.of("ஆயுர்வேத செரிமான ஆதரவு"),
                null,
                "சரங்கதர சம்ஹிதை",
                true,
                true,
                "இந்தியா",
                "இந்தியா",
                null,
                null,
                Language.TA,
                List.of("1:1:1"),
                "இந்தியா",
                "டாபர்",
                "இந்தியா",
                List.of()
        );

        ProductReadinessResponse res = service.analyze(request);

        assertThat(res.language()).isEqualTo(Language.TA);
        assertThat(res.report()).contains("[TRANSLATED-TA]");
        assertThat(res.nextSteps()).allMatch(s -> s.startsWith("[TRANSLATED-TA]"));
        assertThat(res.classification().get("rationale").toString()).startsWith("[TRANSLATED-TA]");
    }

    @Test
    void testI_multilingualTeluguFormulationAnalysis() {
        FormulationRequest request = new FormulationRequest(
                "త్రిఫల గుగ్గులు",
                List.of("కరక్కాయ", "ఉసిరికాయ", "తానికాయ"),
                "టాబ్లెట్",
                "సాంప్రదాయ జీర్ణ సమతుల్యత",
                List.of("ఆయుర్వేద జీర్ణ మద్దతు"),
                null,
                "శారంగధర సంహిత",
                true,
                true,
                "భారతదేశం",
                "భారతదేశం",
                null,
                null,
                Language.TE,
                List.of("1:1:1"),
                "భారతదేశం",
                "డాబర్",
                "భారతదేశం",
                List.of()
        );

        ProductReadinessResponse res = service.analyze(request);

        assertThat(res.language()).isEqualTo(Language.TE);
        assertThat(res.report()).contains("[TRANSLATED-TE]");
        assertThat(res.nextSteps()).allMatch(s -> s.startsWith("[TRANSLATED-TE]"));
        assertThat(res.classification().get("rationale").toString()).startsWith("[TRANSLATED-TE]");
    }
}
