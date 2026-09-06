package com.ipsakti.ip_sakti_backend.formulation;

import com.ipsakti.ip_sakti_backend.formulation.classification.FormulationClarificationService;
import com.ipsakti.ip_sakti_backend.formulation.classification.FormulationRuleAssessment;
import com.ipsakti.ip_sakti_backend.formulation.classification.FormulationRuleEngine;
import com.ipsakti.ip_sakti_backend.formulation.classification.RegulatoryRouteService;
import com.ipsakti.ip_sakti_backend.formulation.model.ClaimAnalysisItem;
import com.ipsakti.ip_sakti_backend.formulation.model.ClaimCategory;
import com.ipsakti.ip_sakti_backend.formulation.model.DocumentGapItem;
import com.ipsakti.ip_sakti_backend.formulation.model.DocumentStatus;
import com.ipsakti.ip_sakti_backend.formulation.model.DocumentType;
import com.ipsakti.ip_sakti_backend.formulation.model.FormulationClassification;
import com.ipsakti.ip_sakti_backend.formulation.model.FormulationRequest;
import com.ipsakti.ip_sakti_backend.formulation.model.IngredientVerificationItem;
import com.ipsakti.ip_sakti_backend.formulation.model.IpRouteAssessment;
import com.ipsakti.ip_sakti_backend.formulation.model.ProductReadinessResponse;
import com.ipsakti.ip_sakti_backend.formulation.model.ProvidedDocument;
import com.ipsakti.ip_sakti_backend.formulation.model.ReadinessScores;
import com.ipsakti.ip_sakti_backend.formulation.model.RegulatoryRoute;
import com.ipsakti.ip_sakti_backend.multilingual.LanguageMetadata;
import com.ipsakti.ip_sakti_backend.multilingual.TranslatedText;
import com.ipsakti.ip_sakti_backend.multilingual.TranslationService;
import com.ipsakti.ip_sakti_backend.question.model.Language;
import com.ipsakti.ip_sakti_backend.question.model.QuestionCitation;
import com.ipsakti.ip_sakti_backend.question.model.QuestionSource;
import com.ipsakti.ip_sakti_backend.rag.RagClient;
import com.ipsakti.ip_sakti_backend.rag.dto.RagAskRequest;
import com.ipsakti.ip_sakti_backend.rag.dto.RagAskResponse;
import com.ipsakti.ip_sakti_backend.rag.dto.RagCitation;
import com.ipsakti.ip_sakti_backend.rag.dto.RagSource;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AyurvedaProductReadinessService {

    private static final Logger log = LoggerFactory.getLogger(AyurvedaProductReadinessService.class);

    private final RagClient ragClient;
    private final FormulationRuleEngine ruleEngine;
    private final FormulationClarificationService clarificationService;
    private final RegulatoryRouteService routeService;
    private final TranslationService translationService;

    public AyurvedaProductReadinessService(
            RagClient ragClient,
            FormulationRuleEngine ruleEngine,
            FormulationClarificationService clarificationService,
            RegulatoryRouteService routeService,
            TranslationService translationService
    ) {
        this.ragClient = ragClient;
        this.ruleEngine = ruleEngine;
        this.clarificationService = clarificationService;
        this.routeService = routeService;
        this.translationService = translationService;
    }

    public ProductReadinessResponse analyze(FormulationRequest request) {
        long started = System.nanoTime();
        String requestId = UUID.randomUUID().toString();

        TranslatedText translatedText = translationService.toCanonical(combinedInput(request), request.language(), requestId);
        LanguageMetadata languageMetadata = translatedText.metadata();
        FormulationRequest canonical = canonicalRequest(request, languageMetadata);

        FormulationRuleAssessment assessment = ruleEngine.assess(canonical);
        FormulationClassification leadingClassification = assessment.leadingClassification();
        String jurisdiction = formulationJurisdiction(canonical);
        RegulatoryRoute regulatoryRoute = routeService.routeFor(leadingClassification, jurisdiction);

        // 1. Multi-domain RAG queries
        List<RagCitation> accumulatedCitations = new ArrayList<>();
        List<RagSource> accumulatedSources = new ArrayList<>();

        // RAG query 1: Primary Regulatory Domain
        String primaryDomain = ragDomainFor(leadingClassification);
        try {
            RagAskResponse primaryRag = ragClient.ask(new RagAskRequest(
                    buildRegulatoryRagQuery(canonical, leadingClassification),
                    primaryDomain,
                    "INDIA",
                    5
            ));
            if (primaryRag != null && !Boolean.TRUE.equals(primaryRag.abstained())) {
                accumulatedCitations.addAll(primaryRag.citations());
                accumulatedSources.addAll(primaryRag.sources());
            }
        } catch (Exception e) {
            log.warn("Regulatory RAG retrieval encountered issue: {}", e.getMessage());
        }

        // RAG query 2: Traditional Knowledge & Patent Exclusions (Section 3p / Section 3e)
        if (Boolean.TRUE.equals(canonical.traditionalUse()) || hasClassicalKeywords(canonical)) {
            try {
                RagAskResponse tkRag = ragClient.ask(new RagAskRequest(
                        "Section 3(p) traditional knowledge patent exclusion and Section 3(e) admixture in Indian patent law",
                        "PATENT",
                        "INDIA",
                        4
                ));
                if (tkRag != null && !Boolean.TRUE.equals(tkRag.abstained())) {
                    accumulatedCitations.addAll(tkRag.citations());
                    accumulatedSources.addAll(tkRag.sources());
                }
            } catch (Exception e) {
                log.warn("TK RAG retrieval encountered issue: {}", e.getMessage());
            }
        }

        // RAG query 3: Biodiversity / ABS
        if (hasBiologicalResourceSignal(canonical)) {
            try {
                RagAskResponse absRag = ragClient.ask(new RagAskRequest(
                        "Biological Diversity Act Section 6 National Biodiversity Authority approval for intellectual property rights",
                        "ABS",
                        "INDIA",
                        4
                ));
                if (absRag != null && !Boolean.TRUE.equals(absRag.abstained())) {
                    accumulatedCitations.addAll(absRag.citations());
                    accumulatedSources.addAll(absRag.sources());
                }
            } catch (Exception e) {
                log.warn("ABS RAG retrieval encountered issue: {}", e.getMessage());
            }
        }

        List<QuestionCitation> mappedCitations = deduplicateCitations(mapCitations(accumulatedCitations));
        List<QuestionSource> mappedSources = deduplicateSources(mapSources(accumulatedSources));

        // 2. Document Gap Analysis
        List<DocumentGapItem> gaps = performDocumentGapAnalysis(canonical, leadingClassification);

        // 3. Claims Analysis
        List<ClaimAnalysisItem> claimsAnalysis = performClaimsAnalysis(canonical);

        // 4. Ingredient Verification
        List<IngredientVerificationItem> ingredientAnalysis = performIngredientVerification(canonical);

        // 5. Traditional Knowledge & Biodiversity / ABS Analysis
        Map<String, Object> tkAnalysis = buildTraditionalKnowledgeAnalysis(canonical, leadingClassification);
        Map<String, Object> absAnalysis = buildBiodiversityAbsAnalysis(canonical);

        // 6. IP Protection Analysis
        Map<String, Object> ipAnalysis = buildIpProtectionAnalysis(canonical, leadingClassification);

        // 7. Documents Evaluation
        Map<String, Object> documentsEval = buildDocumentsEvaluation(canonical);

        // 8. Discrete Readiness Scores
        ReadinessScores scores = calculateReadinessScores(
                assessment, gaps, claimsAnalysis, ingredientAnalysis, tkAnalysis, absAnalysis, ipAnalysis
        );

        // 9. Recommended Next Steps
        List<String> nextSteps = generateRecommendedNextSteps(scores, leadingClassification, gaps, claimsAnalysis, absAnalysis);

        // 10. Clarification Questions (max 3)
        List<String> questions = clarificationService.questionsFor(assessment).stream().limit(3).toList();

        // 11. Calculate Overall Confidence
        double confidence = calculateConfidence(assessment, mappedCitations, gaps, scores);

        // 12. Determine Status & Abstention
        boolean abstained = mappedCitations.isEmpty() && (scores.overall().equals("INSUFFICIENT_EVIDENCE") || assessment.hasConflict());
        String status = determineStatus(assessment, scores);

        // 13. Assemble 17-Section Report
        String report = generate17SectionReport(
                canonical, leadingClassification, regulatoryRoute, gaps, claimsAnalysis,
                ingredientAnalysis, tkAnalysis, absAnalysis, ipAnalysis, scores, nextSteps, mappedCitations, confidence
        );

        // Map classification & regulatory objects
        Map<String, Object> productMap = Map.of(
                "name", canonical.productName() != null ? canonical.productName() : "Unnamed Product",
                "dosageForm", canonical.dosageForm() != null ? canonical.dosageForm() : "Unspecified",
                "intendedUse", canonical.intendedUse() != null ? canonical.intendedUse() : "Unspecified",
                "manufacturer", canonical.manufacturer() != null ? canonical.manufacturer() : "Unspecified",
                "countryOfManufacture", canonical.countryOfManufacture() != null ? canonical.countryOfManufacture() : "India",
                "targetMarket", canonical.targetMarket() != null ? canonical.targetMarket() : "India"
        );

        Map<String, Object> classificationMap = Map.of(
                "category", mapToPublicCategory(leadingClassification),
                "internalCode", leadingClassification.name(),
                "confidence", confidence,
                "status", scores.regulatoryClassification(),
                "rationale", getCategoryRationale(leadingClassification, canonical)
        );

        Map<String, Object> regulatoryMap = Map.of(
                "pathway", regulatoryRoute != null ? regulatoryRoute.route() : "AYUSH_STATE_LICENSING",
                "governingFramework", getGoverningFramework(leadingClassification),
                "licensingAuthority", getLicensingAuthority(leadingClassification),
                "standards", getApplicableStandards(leadingClassification)
        );

        // Localize output if requested language is not English
        String localizedReport = report;
        List<String> localizedNextSteps = nextSteps;
        List<String> localizedQuestions = questions;
        Map<String, Object> localizedClassificationMap = classificationMap;
        Map<String, Object> localizedTk = tkAnalysis;
        Map<String, Object> localizedAbs = absAnalysis;

        if (languageMetadata.requestedLanguage() != Language.EN) {
            localizedReport = translateReport(report, languageMetadata, requestId);
            localizedNextSteps = translateList(nextSteps, languageMetadata, requestId);
            localizedQuestions = translateList(questions, languageMetadata, requestId);

            Map<String, Object> classCopy = new LinkedHashMap<>(classificationMap);
            classCopy.put("rationale", translateText((String) classificationMap.get("rationale"), languageMetadata, requestId));
            localizedClassificationMap = classCopy;

            Map<String, Object> tkCopy = new LinkedHashMap<>(tkAnalysis);
            List<String> tkTexts = new ArrayList<>();
            String patentImp = (String) tkCopy.get("patentImplication");
            String tkdlDist = (String) tkCopy.get("tkdlDistinction");
            if (patentImp != null) tkTexts.add(patentImp);
            if (tkdlDist != null) tkTexts.add(tkdlDist);
            if (!tkTexts.isEmpty()) {
                List<String> translatedTk = translateList(tkTexts, languageMetadata, requestId);
                int idx = 0;
                if (patentImp != null && idx < translatedTk.size()) tkCopy.put("patentImplication", translatedTk.get(idx++));
                if (tkdlDist != null && idx < translatedTk.size()) tkCopy.put("tkdlDistinction", translatedTk.get(idx));
            }
            localizedTk = tkCopy;

            Map<String, Object> absCopy = new LinkedHashMap<>(absAnalysis);
            List<String> absTexts = new ArrayList<>();
            String rec = (String) absCopy.get("recommendation");
            String nba = (String) absCopy.get("nbaApprovalRequirement");
            if (rec != null) absTexts.add(rec);
            if (nba != null) absTexts.add(nba);
            if (!absTexts.isEmpty()) {
                List<String> translatedAbs = translateList(absTexts, languageMetadata, requestId);
                int idx = 0;
                if (rec != null && idx < translatedAbs.size()) absCopy.put("recommendation", translatedAbs.get(idx++));
                if (nba != null && idx < translatedAbs.size()) absCopy.put("nbaApprovalRequirement", translatedAbs.get(idx));
            }
            localizedAbs = absCopy;
        }

        ProductReadinessResponse response = new ProductReadinessResponse(
                productMap,
                localizedClassificationMap,
                regulatoryMap,
                documentsEval,
                ingredientAnalysis,
                claimsAnalysis,
                localizedTk,
                localizedAbs,
                ipAnalysis,
                gaps,
                localizedNextSteps,
                mappedCitations,
                mappedSources,
                confidence,
                status,
                abstained,
                scores,
                localizedReport,
                localizedQuestions,
                languageMetadata.requestedLanguage(),
                languageMetadata.detectedLanguage(),
                languageMetadata.processingLanguage()
        );

        log.info(
                "ayurveda_readiness_analysis_completed requestId={} status={} classification={} confidence={} latencyMs={}",
                requestId,
                status,
                leadingClassification,
                confidence,
                Duration.ofNanos(System.nanoTime() - started).toMillis()
        );

        return response;
    }

    private List<DocumentGapItem> performDocumentGapAnalysis(FormulationRequest request, FormulationClassification classification) {
        List<DocumentGapItem> gaps = new ArrayList<>();
        List<ProvidedDocument> docs = request.documents() != null ? request.documents() : List.of();

        // 1. Product formulation specification
        boolean hasFormulationSpec = hasDocType(docs, DocumentType.FORMULATION_SPECIFICATION) || (request.ingredients() != null && !request.ingredients().isEmpty() && request.dosageForm() != null);
        gaps.add(new DocumentGapItem(
                "Product Formulation Specification",
                hasFormulationSpec ? "AVAILABLE" : "MISSING",
                "REQUIRED",
                hasFormulationSpec ? "Formulation details with dosage form and ingredient list provided." : "A complete formulation master specification with ratios and excipients is required for licensing.",
                "Drugs & Cosmetics Rules, Rule 153 / FSSAI Regulations"
        ));

        // 2. Ingredient details & source records
        boolean hasIngSource = hasDocType(docs, DocumentType.INGREDIENT_SOURCE_RECORDS) || request.sourceOfIngredients() != null;
        gaps.add(new DocumentGapItem(
                "Ingredient Source & Botanical Records",
                hasIngSource ? "AVAILABLE" : "UNVERIFIED",
                "REQUIRED",
                hasIngSource ? "Ingredient source information recorded." : "Geographical source, part used, and raw material vendor records require verification.",
                "Ayurvedic Pharmacopoeia of India (API) raw material standards"
        ));

        // 3. Manufacturing Information & GMP
        boolean hasMfg = hasDocType(docs, DocumentType.MANUFACTURING_INFO) || request.manufacturingMethod() != null;
        gaps.add(new DocumentGapItem(
                "Manufacturing Information / Method of Preparation",
                hasMfg ? "AVAILABLE" : "MISSING",
                "REQUIRED",
                hasMfg ? "Process details provided in request." : "Batch manufacturing process flowchart, extraction method, and GMP compliance records required.",
                "Schedule T (Good Manufacturing Practices) of Drugs & Cosmetics Rules"
        ));

        // 4. Quality & Finished Product Testing
        boolean hasQuality = hasDocType(docs, DocumentType.QUALITY_TEST_REPORT);
        gaps.add(new DocumentGapItem(
                "Quality Evidence & Certificate of Analysis",
                hasQuality ? "AVAILABLE" : "UNVERIFIED",
                "REQUIRED",
                hasQuality ? "Quality test report provided." : "Certificate of Analysis covering microbial load, heavy metals, pesticide residues, and aflatoxins required.",
                "Pharmacopoeial Laboratory for Indian Medicine (PLIM) guidelines"
        ));

        // 5. Stability & Shelf-Life Evidence
        boolean hasStability = hasDocType(docs, DocumentType.STABILITY_STUDY);
        gaps.add(new DocumentGapItem(
                "Stability & Shelf-Life Study",
                hasStability ? "AVAILABLE" : "MISSING",
                "RECOMMENDED",
                hasStability ? "Stability records provided." : "Accelerated/real-time stability data to support expiry period per Rule 161-B.",
                "Drugs & Cosmetics Rules, Rule 161-B (Expiry date of Ayurvedic medicines)"
        ));

        // 6. Label & Packaging Mockups
        boolean hasLabel = hasDocType(docs, DocumentType.PRODUCT_LABEL) || hasDocType(docs, DocumentType.PACKAGING_INFO);
        gaps.add(new DocumentGapItem(
                "Label Information & Packaging Details",
                hasLabel ? "AVAILABLE" : "UNVERIFIED",
                "REQUIRED",
                hasLabel ? "Label details referenced." : "Statutory label draft displaying true list of ingredients, batch, manufacturing license number, and warnings required.",
                "Drugs & Cosmetics Rules, Rule 161 (Manner of labelling)"
        ));

        // 7. Classical Formulation Reference
        boolean isClassical = classification == FormulationClassification.CLASSICAL_DRUG || Boolean.TRUE.equals(request.traditionalUse());
        boolean hasClassicalRef = hasDocType(docs, DocumentType.CLASSICAL_TEXT_REFERENCE) || (request.classicalReference() != null && !request.classicalReference().isBlank());
        gaps.add(new DocumentGapItem(
                "Classical Formulary Text Reference",
                isClassical ? (hasClassicalRef ? "AVAILABLE" : "MISSING") : "NOT_APPLICABLE",
                isClassical ? "REQUIRED" : "OPTIONAL",
                isClassical ? (hasClassicalRef ? "Authoritative classical text citation provided." : "Citation from authoritative book specified in First Schedule of Drugs & Cosmetics Act required.") : "Not required for proprietary/food formulations.",
                "First Schedule to the Drugs & Cosmetics Act, 1940"
        ));

        // 8. Traditional-Use Evidence
        boolean hasTkEv = hasDocType(docs, DocumentType.TRADITIONAL_USE_EVIDENCE) || Boolean.TRUE.equals(request.traditionalUse());
        gaps.add(new DocumentGapItem(
                "Traditional-Use Evidence",
                hasTkEv ? "AVAILABLE" : "UNVERIFIED",
                "RECOMMENDED",
                hasTkEv ? "Traditional usage context noted." : "Documentation supporting longstanding traditional use helps substantiating safety.",
                "AYUSH Regulatory Guidelines on Traditional Usage"
        ));

        // 9. Regulatory / License Evidence
        boolean hasLicense = hasDocType(docs, DocumentType.AYUSH_DRUG_LICENSE) || hasDocType(docs, DocumentType.FSSAI_LICENSE) || request.existingLicense() != null;
        gaps.add(new DocumentGapItem(
                "Regulatory / Manufacturing License Evidence",
                hasLicense ? "AVAILABLE" : "MISSING",
                "REQUIRED",
                hasLicense ? "Existing license reference documented." : "Manufacturing license issued by State Licensing Authority (AYUSH) or FSSAI registration required before commercial release.",
                "Section 33EEC, Drugs and Cosmetics Act, 1940 / FSS Act, 2006"
        ));

        // 10. IP Search / Freedom-to-Operate Evidence
        boolean hasIpDoc = hasDocType(docs, DocumentType.PATENT_DOCUMENT) || hasDocType(docs, DocumentType.TRADEMARK_INFO);
        gaps.add(new DocumentGapItem(
                "IP Search & Freedom-to-Operate (FTO) Records",
                hasIpDoc ? "AVAILABLE" : "UNVERIFIED",
                "RECOMMENDED",
                hasIpDoc ? "IP records provided." : "Prior-art search in patent databases and trademark clearance search recommended prior to commercial launch.",
                "Trade Marks Act, 1999 / Patents Act, 1970"
        ));

        // 11. Traditional Knowledge (TK) Search Records
        gaps.add(new DocumentGapItem(
                "Traditional Knowledge Prior-Art Search",
                isClassical ? "AVAILABLE" : "UNVERIFIED",
                "RECOMMENDED",
                isClassical ? "Classical text status established." : "Search against known traditional formulations and TKDL references recommended to assess novelty.",
                "Section 3(p) of the Patents Act, 1970"
        ));

        // 12. Biological Resource Sourcing Evidence
        boolean usesBio = hasBiologicalResourceSignal(request);
        gaps.add(new DocumentGapItem(
                "Biological Resource Sourcing Evidence",
                usesBio ? (hasIngSource ? "AVAILABLE" : "UNVERIFIED") : "NOT_APPLICABLE",
                usesBio ? "REQUIRED" : "OPTIONAL",
                usesBio ? "Documentation on collection/purchase of Indian herbs and biological material." : "Not applicable if purely non-biological.",
                "Biological Diversity Act, 2002"
        ));

        // 13. ABS / National Biodiversity Authority (NBA) Evidence
        boolean hasNba = hasDocType(docs, DocumentType.NBA_ABS_APPROVAL);
        gaps.add(new DocumentGapItem(
                "National Biodiversity Authority (NBA) Approval / ABS Evidence",
                hasNba ? "AVAILABLE" : (usesBio ? "MISSING" : "NOT_APPLICABLE"),
                usesBio ? "REQUIRED" : "OPTIONAL",
                hasNba ? "NBA approval documentation present." : (usesBio ? "Mandatory Section 6 approval required before applying for IP if biological resources from India are utilized." : "Not required."),
                "Section 6 & Section 3, Biological Diversity Act, 2002"
        ));

        return gaps;
    }

    private List<ClaimAnalysisItem> performClaimsAnalysis(FormulationRequest request) {
        List<ClaimAnalysisItem> items = new ArrayList<>();
        List<String> claims = request.claims() != null ? request.claims() : List.of();

        if (claims.isEmpty() && request.intendedUse() != null && !request.intendedUse().isBlank()) {
            claims = List.of(request.intendedUse());
        }

        for (String claim : claims) {
            String lower = claim.toLowerCase(Locale.ROOT);
            ClaimCategory category;
            String implication;
            String evidenceSource;
            String evidenceType;
            boolean hasEvidence = false;

            if (containsAny(lower, "cancer", "diabetes", "cure", "cures", "prevent cancer", "arthritis cure", "aids", "tuberculosis", "epilepsy", "blindness")) {
                category = ClaimCategory.DISEASE_CLAIM;
                implication = "Prohibited disease treatment claim under the Drugs and Magic Remedies (Objectionable Advertisements) Act, 1954. Requires strict regulatory review and cannot be made without clinical trial validation.";
                evidenceSource = "Drugs and Magic Remedies (Objectionable Advertisements) Act, 1954 (Schedule to Section 3)";
                evidenceType = "Statutory Claim Prohibition";
                hasEvidence = true;
            } else if (containsAny(lower, "anti-inflammatory", "analgesic", "pain relief", "treats", "treatment", "therapeutic", "reduction of blood sugar", "hypertension")) {
                category = ClaimCategory.THERAPEUTIC_CLAIM;
                implication = "Therapeutic claim indicates an Ayurvedic Medicine or Phytopharmaceutical Drug pathway. Not permissible on Food/Nutraceutical (Ayurveda Aahara) or Cosmetic labels.";
                evidenceSource = "Drugs & Cosmetics Act, 1940 / FSS (Ayurveda Aahara) Regulations, 2022";
                evidenceType = "Drug Classification Boundary";
                hasEvidence = true;
            } else if (containsAny(lower, "nutritional", "protein", "vitamin", "mineral", "dietary supplement", "dietary", "nourishment")) {
                category = ClaimCategory.NUTRITIONAL_CLAIM;
                implication = "Nutritional claim consistent with FSSAI Ayurveda Aahara or Health Supplement positioning under Food Safety and Standards Act.";
                evidenceSource = "FSSAI (Health Supplements, Nutraceuticals, Food for Special Dietary Use) Regulations";
                evidenceType = "Nutritional Claim Framework";
                hasEvidence = true;
            } else if (containsAny(lower, "classical", "traditional", "rasayana", "rejuvenation", "as per ayurvedic formulary", "described in texts")) {
                category = ClaimCategory.TRADITIONAL_USE;
                implication = "Traditional usage claim allowable for Classical Ayurvedic Formulations when backed by citations from books specified in the First Schedule.";
                evidenceSource = "First Schedule to the Drugs and Cosmetics Act, 1940";
                evidenceType = "Traditional Use Substantiation";
                hasEvidence = true;
            } else if (containsAny(lower, "supports", "wellness", "digestion", "immunity", "vitality", "general health", "promotes", "lifestyle")) {
                category = ClaimCategory.GENERAL_WELLNESS;
                implication = "General wellness claim allowable across both Ayurvedic proprietary medicines and Ayurveda Aahara food products if substantiated.";
                evidenceSource = "Ministry of AYUSH & FSSAI Guidelines on General Wellness Claims";
                evidenceType = "Wellness Claim Guidance";
                hasEvidence = true;
            } else {
                category = ClaimCategory.REQUIRES_REGULATORY_REVIEW;
                implication = "Positioning requires review by regulatory counsel to ensure compliance with applicable advertising and labelling rules.";
                evidenceSource = "Corpus legal guidelines";
                evidenceType = "General Regulatory Review";
                hasEvidence = false;
            }

            items.add(new ClaimAnalysisItem(claim, category, hasEvidence, evidenceSource, evidenceType, implication));
        }

        return items;
    }

    private List<IngredientVerificationItem> performIngredientVerification(FormulationRequest request) {
        List<IngredientVerificationItem> items = new ArrayList<>();
        List<String> ingredients = request.ingredients() != null ? request.ingredients() : List.of();
        List<String> ratios = request.ingredientRatios() != null ? request.ingredientRatios() : List.of();

        for (int i = 0; i < ingredients.size(); i++) {
            String ing = ingredients.get(i);
            String ratio = i < ratios.size() ? ratios.get(i) : null;
            String lower = ing.toLowerCase(Locale.ROOT);

            String botanicalName = extractParenthetical(ing);
            boolean isBio = !containsAny(lower, "excipient", "preservative", "water", "ethanol", "silica", "binder", "gelatin");

            boolean recognizedAyurvedic = containsAny(lower,
                    "triphala", "guggulu", "haritaki", "bibhitaki", "amalaki", "ashwagandha", "tulsi",
                    "curcumin", "turmeric", "neem", "ginger", "cardamom", "cinnamon", "piperine",
                    "terminalia", "emblica", "commiphora", "withania", "ocimum", "zingiber", "boswellia",
                    "elettaria", "cinnamomum", "brahmi", "bacopa", "shatavari", "asparagus", "arjuna",
                    "licorice", "glycyrrhiza", "churna", "taila", "bhasma", "kwath");

            String status = recognizedAyurvedic ? "VERIFIED" : "UNVERIFIED";
            String evidenceSource = recognizedAyurvedic ? "Recognized in Ayurvedic Pharmacopoeia of India (API) / Classical Materia Medica" : null;
            String tradEvidence = recognizedAyurvedic ? "Extensive documented classical usage in Ayurvedic texts (Charaka, Sushruta, Astanga Hridaya)" : null;
            String uncertainty = recognizedAyurvedic ? "Identity recognized; specific grade, batch assay, and active constituent standardization require laboratory verification."
                    : "Botanical taxonomy, safety data, and pharmacopoeial monograph require independent verification.";

            items.add(new IngredientVerificationItem(
                    ing,
                    botanicalName,
                    ratio,
                    recognizedAyurvedic ? "Active Ayurvedic Ingredient" : "Botanical/Formulation Component",
                    evidenceSource,
                    tradEvidence,
                    isBio,
                    status,
                    uncertainty
            ));
        }

        return items;
    }

    private Map<String, Object> buildTraditionalKnowledgeAnalysis(FormulationRequest request, FormulationClassification classification) {
        boolean isClassical = classification == FormulationClassification.CLASSICAL_DRUG || Boolean.TRUE.equals(request.traditionalUse());
        boolean hasClassicalReference = request.classicalReference() != null && !request.classicalReference().isBlank();
        boolean hasTkSignal = isClassical || hasClassicalKeywords(request);

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("status", hasTkSignal ? "CONFIRMED" : "NONE_IDENTIFIED");
        map.put("isClassicalFormulation", isClassical);
        map.put("classicalReferenceProvided", hasClassicalReference);
        map.put("classicalReferenceText", request.classicalReference());
        map.put("tkdlDistinction", "Important Distinction: Traditional Knowledge in the public domain (documented in authoritative texts) is distinct from the Traditional Knowledge Digital Library (TKDL) database. TKDL is a specialized digital repository utilized primarily by patent examiners worldwide to cite prior art and prevent biopiracy.");
        map.put("patentImplication", "Section 3(p) of the Indian Patents Act, 1970 strictly excludes from patentability any invention which in effect is traditional knowledge or an aggregation/duplication of known properties of traditionally known component(s). Standalone classical formulations cannot be patented in India.");
        map.put("summary", hasTkSignal ? "The product appears to draw from documented traditional knowledge. Classical formulation recipes enjoy established traditional use safety substantiation, but face absolute patent exclusions under Section 3(p)." : "No significant traditional knowledge reliance identified based on the supplied details.");

        return map;
    }

    private Map<String, Object> buildBiodiversityAbsAnalysis(FormulationRequest request) {
        boolean hasBioSignal = hasBiologicalResourceSignal(request);
        Map<String, Object> map = new LinkedHashMap<>();

        map.put("status", hasBioSignal ? "POTENTIALLY_RELEVANT" : "NOT_INDICATED");
        map.put("biologicalResourceUsed", hasBioSignal);
        map.put("sourceOfIngredients", request.sourceOfIngredients() != null ? request.sourceOfIngredients() : "Unspecified");
        map.put("nbaApprovalRequirement", "Section 6 of the Biological Diversity Act, 2002: Any person applying for any intellectual property right, in or outside India, for any invention based on any biological resource obtained from India or associated traditional knowledge, must obtain the prior approval of the National Biodiversity Authority (NBA).");
        map.put("exemptionsAndAmendments", "Under the Biological Diversity (Amendment) Act, 2023, cultivated medicinal plants and AYUSH practitioners enjoy certain access procedural exemptions, but commercial IP filings based on Indian genetic resources remain subject to Section 6 review.");
        map.put("recommendation", hasBioSignal ? "Ensure transparent traceability for raw botanical materials and obtain necessary NBA approvals or State Biodiversity Board (SBB) intimations prior to filing patent applications or commercialization." : "Not indicated as relevant based on the current product information.");

        return map;
    }

    private Map<String, Object> buildIpProtectionAnalysis(FormulationRequest request, FormulationClassification classification) {
        List<IpRouteAssessment> routes = new ArrayList<>();
        boolean isClassical = classification == FormulationClassification.CLASSICAL_DRUG || Boolean.TRUE.equals(request.traditionalUse());

        // 1. Trademark
        routes.add(new IpRouteAssessment(
                "TRADEMARK",
                "HIGH",
                "A distinctive brand name, product logo, and proprietary packaging trade dress are protectable under the Trade Marks Act, 1999 (e.g. in Class 5 for pharmaceuticals/dietetic, Class 3 for cosmetics, or Class 30 for teas/infusions).",
                "Trade Marks Act, 1999"
        ));

        // 2. Patent
        if (isClassical) {
            routes.add(new IpRouteAssessment(
                    "PATENT",
                    "LOW",
                    "A classical Ayurveda formulation is excluded from patentability under Section 3(p) of the Patents Act, 1970 as traditional knowledge. Only novel, non-obvious extraction technologies, delivery matrices, or synergistic synthetic modifications could be considered.",
                    "Section 3(p) & Section 3(e), Patents Act, 1970"
            ));
        } else {
            routes.add(new IpRouteAssessment(
                    "PATENT",
                    "MEDIUM",
                    "A patent route may be relevant if the invention involves a novel, non-obvious technical solution (e.g., specialized delivery system, liposomal carrier, or synergistic extraction method) that overcomes Section 3(p) (traditional knowledge) and Section 3(e) (mere admixture).",
                    "Patents Act, 1970 (Section 2(1)(j), Section 3(e), Section 3(p))"
            ));
        }

        // 3. Design
        routes.add(new IpRouteAssessment(
                "DESIGN",
                "MEDIUM",
                "Novel and aesthetically unique product container shapes, blister patterns, or bottle configurations can be registered under the Designs Act, 2000.",
                "Designs Act, 2000"
        ));

        // 4. Copyright
        routes.add(new IpRouteAssessment(
                "COPYRIGHT",
                "HIGH",
                "Original artwork, label layouts, product brochures, and informational literature are protected automatically as artistic and literary works under the Copyright Act, 1957.",
                "Copyright Act, 1957"
        ));

        // 5. Trade Secret
        routes.add(new IpRouteAssessment(
                "TRADE_SECRET",
                "MEDIUM",
                "Proprietary manufacturing parameters, specific temperature/pressure profiles during extraction, and quality protocols may be protected via confidential internal agreements and NDAs.",
                "Indian Contract Act, 1872 & Common Law of Breach of Confidence"
        ));

        // 6. Geographical Indication
        boolean mentionsOrigin = containsAny(combinedInput(request).toLowerCase(Locale.ROOT), "malabar", "kashmir", "darjeeling", "western ghats", "assam", "kerala", "gir");
        routes.add(new IpRouteAssessment(
                "GEOGRAPHICAL_INDICATION",
                mentionsOrigin ? "HIGH" : "LOW",
                mentionsOrigin ? "If raw materials originate from a designated GI region, registration as an 'Authorized User' under the Geographical Indications of Goods Act, 1999 can provide exclusive marketing authenticity."
                        : "Potentially relevant only if specific origin-linked raw materials with registered GI status in India are utilized.",
                "Geographical Indications of Goods (Registration and Protection) Act, 1999"
        ));

        Map<String, Object> ipMap = new LinkedHashMap<>();
        ipMap.put("routes", routes);
        ipMap.put("summary", "Brand identity (Trademark), packaging design (Design), and original labels (Copyright) represent the most immediately viable IP routes. Patenting requires overcoming Section 3(p) and 3(e) exclusions.");
        return ipMap;
    }

    private Map<String, Object> buildDocumentsEvaluation(FormulationRequest request) {
        List<ProvidedDocument> docs = request.documents() != null ? request.documents() : List.of();
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("totalProvided", docs.size());
        map.put("documents", docs);
        map.put("verificationNotice", "Documents referenced by the user are candidate records and require verification against primary regulatory bodies or accredited analytical laboratories prior to formal filing.");
        return map;
    }

    private ReadinessScores calculateReadinessScores(
            FormulationRuleAssessment assessment,
            List<DocumentGapItem> gaps,
            List<ClaimAnalysisItem> claims,
            List<IngredientVerificationItem> ingredients,
            Map<String, Object> tk,
            Map<String, Object> abs,
            Map<String, Object> ip
    ) {
        // Regulatory Classification
        String regClass = assessment.hasConflict() ? "UNCLEAR" : (assessment.leadingScore() >= 3 ? "CONFIRMED" : "LIKELY");

        // Document Completeness
        long missingRequired = gaps.stream().filter(g -> "REQUIRED".equals(g.importance()) && "MISSING".equals(g.status())).count();
        String docComp = missingRequired == 0 ? "COMPLETE" : (missingRequired <= 3 ? "PARTIAL" : "INSUFFICIENT_EVIDENCE");

        // Claims Concern
        boolean hasDisease = claims.stream().anyMatch(c -> c.category() == ClaimCategory.DISEASE_CLAIM);
        boolean hasTherapeutic = claims.stream().anyMatch(c -> c.category() == ClaimCategory.THERAPEUTIC_CLAIM || c.category() == ClaimCategory.REQUIRES_REGULATORY_REVIEW);
        String claimsConcern = hasDisease ? "HIGH_REVIEW_REQUIRED" : (hasTherapeutic ? "REVIEW_REQUIRED" : "LOW_CONCERN");

        // Ingredient Verification
        long unverified = ingredients.stream().filter(i -> "UNVERIFIED".equals(i.status())).count();
        String ingVer = ingredients.isEmpty() ? "INSUFFICIENT" : (unverified == 0 ? "VERIFIED" : "PARTIAL");

        // TK
        String tkStatus = (String) tk.getOrDefault("status", "NONE_IDENTIFIED");

        // ABS
        String absStatus = (String) abs.getOrDefault("status", "NOT_INDICATED");

        // IP
        String ipStatus = "POTENTIAL_ROUTES_IDENTIFIED";

        // Overall
        String overall;
        if (hasDisease) {
            overall = "REQUIRES_REGULATORY_REVIEW";
        } else if (missingRequired > 2) {
            overall = "REQUIRES_DOCUMENT_COMPLETION";
        } else if (regClass.equals("UNCLEAR")) {
            overall = "REQUIRES_REGULATORY_REVIEW";
        } else if (tkStatus.equals("CONFIRMED") && absStatus.equals("POTENTIALLY_RELEVANT")) {
            overall = "REQUIRES_IP_REVIEW";
        } else {
            overall = "LOW_RISK_FOR_NEXT_REVIEW";
        }

        return new ReadinessScores(regClass, docComp, claimsConcern, ingVer, tkStatus, absStatus, ipStatus, overall);
    }

    private List<String> generateRecommendedNextSteps(
            ReadinessScores scores,
            FormulationClassification classification,
            List<DocumentGapItem> gaps,
            List<ClaimAnalysisItem> claims,
            Map<String, Object> abs
    ) {
        List<String> steps = new ArrayList<>();
        steps.add("1. Clarify and finalize product regulatory positioning (Ayurvedic Medicine vs. Ayurveda Aahara vs. Cosmetic).");

        if (classification == FormulationClassification.CLASSICAL_DRUG) {
            steps.add("2. Document exact textual citation from the First Schedule classical text and ensure adherence to prescribed classical processing.");
        } else {
            steps.add("2. Compile batch manufacturing formula, standardized botanical extract specifications, and Certificate of Analysis from a NABL-accredited laboratory.");
        }

        if (scores.claims().equals("HIGH_REVIEW_REQUIRED")) {
            steps.add("3. Remove or rephrase prohibited disease prevention/treatment claims to comply with the Drugs and Magic Remedies (Objectionable Advertisements) Act, 1954.");
        } else {
            steps.add("3. Review marketing and packaging claims to ensure they align with the chosen licensing pathway (general wellness vs. therapeutic).");
        }

        steps.add("4. Conduct Trademark clearance search on the IP India public portal for the proposed product brand name across relevant classes.");

        if ("POTENTIALLY_RELEVANT".equals(abs.get("status"))) {
            steps.add("5. Check National Biodiversity Authority (NBA) approval requirements under Section 6 of Biological Diversity Act if patent applications are contemplated.");
        }

        steps.add("6. Prepare statutory label mockup strictly complying with Rule 161 of the Drugs & Cosmetics Rules or FSSAI labelling regulations.");
        steps.add("7. Seek formal pre-submission review with an accredited regulatory counsel or State Licensing Authority (AYUSH).");

        return steps;
    }

    private String generate17SectionReport(
            FormulationRequest request,
            FormulationClassification classification,
            RegulatoryRoute route,
            List<DocumentGapItem> gaps,
            List<ClaimAnalysisItem> claims,
            List<IngredientVerificationItem> ingredients,
            Map<String, Object> tk,
            Map<String, Object> abs,
            Map<String, Object> ip,
            ReadinessScores scores,
            List<String> nextSteps,
            List<QuestionCitation> citations,
            double confidence
    ) {
        StringBuilder sb = new StringBuilder();

        sb.append("# AYURVEDA PRODUCT ASSESSMENT\n\n");
        sb.append("> **Disclaimer**: This assessment is an AI-assisted regulatory information and document-verification pre-screening report. ");
        sb.append("It does not constitute official regulatory approval, licensing, legal certification, or a patent guarantee. ");
        sb.append("All recommendations are advisory and require formal human verification by authorized regulatory bodies.\n\n");

        sb.append("## 1. Product Summary\n");
        sb.append("- **Product Name**: ").append(request.productName()).append("\n");
        sb.append("- **Dosage Form**: ").append(request.dosageForm() != null ? request.dosageForm() : "Unspecified").append("\n");
        sb.append("- **Intended Use**: ").append(request.intendedUse() != null ? request.intendedUse() : "Unspecified").append("\n");
        sb.append("- **Manufacturer**: ").append(request.manufacturer() != null ? request.manufacturer() : "Not disclosed").append("\n");
        sb.append("- **Target Market**: ").append(request.targetMarket() != null ? request.targetMarket() : "India").append("\n\n");

        sb.append("## 2. Preliminary Product Category\n");
        sb.append("- **Category**: **").append(mapToPublicCategory(classification)).append("**\n");
        sb.append("- **Status**: ").append(scores.regulatoryClassification()).append("\n");
        sb.append("- **Rationale**: ").append(getCategoryRationale(classification, request)).append("\n\n");

        sb.append("## 3. Regulatory Pathway to Investigate\n");
        sb.append("- **Pathway Route**: ").append(route != null ? route.route().replace('_', ' ') : "State Licensing Authority (AYUSH)").append("\n");
        sb.append("- **Governing Framework**: ").append(getGoverningFramework(classification)).append("\n");
        sb.append("- **Licensing Authority**: ").append(getLicensingAuthority(classification)).append("\n\n");

        sb.append("## 4. Evidence Found\n");
        sb.append("Retrieved and evaluated authoritative corpus evidence across Indian statutes, Pharmacopoeial standards, and regulatory rules. Total citations validated: ").append(citations.size()).append(".\n\n");

        sb.append("## 5. Documents Verified\n");
        long providedCount = request.documents() != null ? request.documents().size() : 0;
        sb.append("- Documents Referenced by User: ").append(providedCount).append("\n");
        sb.append("- Verification Standard: Referenced documents are candidate evidence requiring certified laboratory and statutory authority endorsement.\n\n");

        sb.append("## 6. Documents Missing / Unverified\n");
        for (DocumentGapItem gap : gaps) {
            if ("MISSING".equals(gap.status()) || "UNVERIFIED".equals(gap.status())) {
                sb.append("- **[").append(gap.status()).append("]** ").append(gap.documentArea()).append(" (").append(gap.importance()).append("): ").append(gap.reason()).append("\n");
            }
        }
        sb.append("\n");

        sb.append("## 7. Ingredient Verification\n");
        for (IngredientVerificationItem item : ingredients) {
            sb.append("- **").append(item.suppliedName()).append("**");
            if (item.botanicalName() != null) sb.append(" (").append(item.botanicalName()).append(")");
            sb.append(": ").append(item.status()).append(" — ").append(item.uncertainty()).append("\n");
        }
        sb.append("\n");

        sb.append("## 8. Claims Analysis\n");
        for (ClaimAnalysisItem claim : claims) {
            sb.append("- **\"").append(claim.claimText()).append("\"** → Category: `").append(claim.category()).append("`\n");
            sb.append("  - *Implication*: ").append(claim.regulatoryImplication()).append("\n");
            sb.append("  - *Reference*: ").append(claim.evidenceSource()).append("\n");
        }
        sb.append("\n");

        sb.append("## 9. Traditional Knowledge Analysis\n");
        sb.append("- **TK Status**: ").append(tk.get("status")).append("\n");
        sb.append("- **Classical Status**: ").append(Boolean.TRUE.equals(tk.get("isClassicalFormulation")) ? "Classical Formulation" : "Non-classical / Proprietary").append("\n");
        sb.append("- **TKDL Distinction**: ").append(tk.get("tkdlDistinction")).append("\n");
        sb.append("- **Section 3(p) Patent Exclusion**: ").append(tk.get("patentImplication")).append("\n\n");

        sb.append("## 10. Biodiversity / ABS Analysis\n");
        sb.append("- **Biological Resources Signal**: ").append(Boolean.TRUE.equals(abs.get("biologicalResourceUsed")) ? "Indicated" : "Not indicated").append("\n");
        sb.append("- **NBA Section 6 Requirement**: ").append(abs.get("nbaApprovalRequirement")).append("\n");
        sb.append("- **Guidance**: ").append(abs.get("recommendation")).append("\n\n");

        sb.append("## 11. IP Protection Opportunities\n");
        @SuppressWarnings("unchecked")
        List<IpRouteAssessment> routes = (List<IpRouteAssessment>) ip.get("routes");
        if (routes != null) {
            for (IpRouteAssessment r : routes) {
                sb.append("- **").append(r.route()).append("** [Relevance: ").append(r.relevance()).append("]: ").append(r.reason()).append("\n");
            }
        }
        sb.append("\n");

        sb.append("## 12. Patent / Prior-Art Considerations\n");
        sb.append("Under Indian patent jurisprudence, natural botanical extracts and traditional Ayurvedic compositions face stringent scrutiny under Section 3(p) (traditional knowledge) and Section 3(e) (mere admixture). ");
        sb.append("A product is never automatically 'patentable'; eligibility requires demonstrating surprising synergistic technical effect beyond the additive properties of known herbs.\n\n");

        sb.append("## 13. Key Risks / Review Points\n");
        sb.append("- **Overall Risk Positioning**: `").append(scores.overall()).append("`\n");
        sb.append("- **Claims Risk**: `").append(scores.claims()).append("`\n");
        sb.append("- **Document Completeness**: `").append(scores.documentCompleteness()).append("`\n\n");

        sb.append("## 14. Recommended Next Steps\n");
        for (String step : nextSteps) {
            sb.append(step).append("\n");
        }
        sb.append("\n");

        sb.append("## 15. Evidence Citations\n");
        if (citations.isEmpty()) {
            sb.append("No specific statutory chunk citations attached for this evaluation.\n\n");
        } else {
            for (int i = 0; i < citations.size(); i++) {
                QuestionCitation c = citations.get(i);
                sb.append(i + 1).append(". **").append(c.document()).append("** (Doc ID: `").append(c.documentId()).append("`");
                if (c.section() != null) sb.append(", Sec: ").append(c.section());
                sb.append(")\n");
            }
            sb.append("\n");
        }

        sb.append("## 16. Confidence\n");
        sb.append("- **Assessment Confidence**: ").append(String.format(Locale.US, "%.1f%%", confidence * 100.0)).append("\n\n");

        sb.append("## 17. Abstention / Limitations\n");
        sb.append("The system cannot independently verify physical product safety, chemical adulteration, clinical efficacy, or formal licensing compliance without primary testing and statutory regulatory inspection.\n");

        return sb.toString();
    }

    private String determineStatus(FormulationRuleAssessment assessment, ReadinessScores scores) {
        if (scores.overall().equals("INSUFFICIENT_EVIDENCE")) return "INSUFFICIENT_EVIDENCE";
        if (assessment.hasConflict()) return "REQUIRES_CLARIFICATION";
        if (scores.claims().equals("HIGH_REVIEW_REQUIRED")) return "REQUIRES_REGULATORY_REVIEW";
        if (scores.documentCompleteness().equals("INSUFFICIENT_EVIDENCE")) return "REQUIRES_DOCUMENT_COMPLETION";
        return "READY_FOR_FURTHER_REGULATORY_REVIEW";
    }

    private double calculateConfidence(FormulationRuleAssessment assessment, List<QuestionCitation> citations, List<DocumentGapItem> gaps, ReadinessScores scores) {
        double conf = 0.50;
        conf += Math.min(assessment.leadingScore(), 4) * 0.08;
        conf += !citations.isEmpty() ? 0.15 : 0.0;
        long missingRequired = gaps.stream().filter(g -> "REQUIRED".equals(g.importance()) && "MISSING".equals(g.status())).count();
        conf -= missingRequired * 0.04;
        if (scores.overall().equals("REQUIRES_REGULATORY_REVIEW")) conf -= 0.05;
        return Math.round(Math.max(0.30, Math.min(0.95, conf)) * 1000.0) / 1000.0;
    }

    private String mapToPublicCategory(FormulationClassification classification) {
        return switch (classification) {
            case CLASSICAL_DRUG -> "CLASSICAL_AYURVEDIC_FORMULATION";
            case PATENT_PROPRIETARY -> "AYURVEDIC_PROPRIETARY_MEDICINE";
            case PHYTOPHARMACEUTICAL_NEW_DRUG -> "AYURVEDIC_MEDICINE";
            case AYURVEDA_AAHAR_NUTRACEUTICAL -> "FOOD_OR_NUTRACEUTICAL";
            case COSMETIC -> "COSMETIC_OR_PERSONAL_CARE";
        };
    }

    private String getCategoryRationale(FormulationClassification classification, FormulationRequest request) {
        return switch (classification) {
            case CLASSICAL_DRUG -> "Product is formulated strictly in accordance with classical formulas described in authoritative Ayurvedic treatises recognized under the First Schedule to the Drugs and Cosmetics Act, 1940.";
            case PATENT_PROPRIETARY -> "Contains Ayurvedic ingredients with novel processing, customized ingredient combinations, or proprietary delivery forms not documented verbatim in the First Schedule classical texts.";
            case PHYTOPHARMACEUTICAL_NEW_DRUG -> "Contains standardized botanical fractions or quantified phytopharmaceutical active constituents developed for specific therapeutic drug applications.";
            case AYURVEDA_AAHAR_NUTRACEUTICAL -> "Positioned as a dietary supplement, herbal tea, or wellness food product regulated under FSSAI Ayurveda Aahara regulations.";
            case COSMETIC -> "Formulated for topical application to skin, hair, or body for cleansing, beautifying, or promoting attractiveness without systemic therapeutic claims.";
        };
    }

    private String getGoverningFramework(FormulationClassification classification) {
        return switch (classification) {
            case CLASSICAL_DRUG -> "Drugs and Cosmetics Act, 1940 (Chapter IV-A) & Drugs and Cosmetics Rules, 1945";
            case PATENT_PROPRIETARY -> "Drugs and Cosmetics Act, 1940 (Section 3(h)) & AYUSH Proprietary Medicine Guidelines";
            case PHYTOPHARMACEUTICAL_NEW_DRUG -> "Drugs and Cosmetics Rules, 1945 (Rule 122-E, Phytopharmaceutical Drugs)";
            case AYURVEDA_AAHAR_NUTRACEUTICAL -> "Food Safety and Standards (Ayurveda Aahara) Regulations, 2022 (FSSAI)";
            case COSMETIC -> "Cosmetics Rules, 2020 under the Drugs and Cosmetics Act, 1940";
        };
    }

    private String getLicensingAuthority(FormulationClassification classification) {
        return switch (classification) {
            case CLASSICAL_DRUG, PATENT_PROPRIETARY, PHYTOPHARMACEUTICAL_NEW_DRUG -> "State Licensing Authority (AYUSH / ISM)";
            case AYURVEDA_AAHAR_NUTRACEUTICAL -> "Food Safety and Standards Authority of India (FSSAI) / State Food Safety Authority";
            case COSMETIC -> "State Licensing Authority (Cosmetics)";
        };
    }

    private String getApplicableStandards(FormulationClassification classification) {
        return switch (classification) {
            case CLASSICAL_DRUG -> "Ayurvedic Pharmacopoeia of India (API) & Ayurvedic Formulary of India (AFI)";
            case PATENT_PROPRIETARY, PHYTOPHARMACEUTICAL_NEW_DRUG -> "In-house analytical specifications validated against API botanical monographs & Schedule T GMP";
            case AYURVEDA_AAHAR_NUTRACEUTICAL -> "FSSAI Food Safety Standards & Schedule T / FSSAI GMP";
            case COSMETIC -> "Bureau of Indian Standards (BIS) Cosmetic Standards & Schedule M-II / Cosmetics Rules 2020";
        };
    }

    private boolean hasDocType(List<ProvidedDocument> docs, DocumentType type) {
        return docs.stream().anyMatch(d -> d.type() == type);
    }

    private boolean hasClassicalKeywords(FormulationRequest request) {
        String text = combinedInput(request).toLowerCase(Locale.ROOT);
        return containsAny(text, "classical", "traditional", "churna", "taila", "guggulu", "kwath", "bhasma", "asava", "arishta", "avaleha", "vati", "gutika", "triphala");
    }

    private boolean hasBiologicalResourceSignal(FormulationRequest request) {
        String text = combinedInput(request).toLowerCase(Locale.ROOT);
        return containsAny(text, "plant", "herb", "herbal", "root", "leaf", "bark", "extract", "seed", "rhizome", "resin", "botanical", "biological", "western ghats", "himalayas", "india");
    }

    private String buildRegulatoryRagQuery(FormulationRequest request, FormulationClassification classification) {
        return switch (classification) {
            case CLASSICAL_DRUG -> "Drugs and Cosmetics Act Ayurvedic classical medicine First Schedule requirements";
            case PATENT_PROPRIETARY -> "Ayurvedic proprietary medicine licensing requirements Drugs and Cosmetics Act";
            case AYURVEDA_AAHAR_NUTRACEUTICAL -> "Ayurveda Aahara regulations 2022 FSSAI food safety requirements";
            case COSMETIC -> "Cosmetic regulations Ayurvedic ingredients topical application";
            case PHYTOPHARMACEUTICAL_NEW_DRUG -> "Phytopharmaceutical drug rules clinical trial requirements CDSCO";
        };
    }

    private String ragDomainFor(FormulationClassification classification) {
        if (classification == FormulationClassification.AYURVEDA_AAHAR_NUTRACEUTICAL) {
            return "FOOD";
        }
        return "PATENT";
    }

    private String formulationJurisdiction(FormulationRequest request) {
        String market = (nullToEmpty(request.targetMarket()) + " " + nullToEmpty(request.country())).toLowerCase(Locale.ROOT);
        if (market.contains("international") || market.contains("global") || market.contains("wipo")
                || market.contains("usa") || market.contains("europe") || market.contains("eu")) {
            return "INTERNATIONAL";
        }
        return "INDIA";
    }

    private String combinedInput(FormulationRequest request) {
        return String.join(" ",
                nullToEmpty(request.productName()),
                request.ingredients() == null ? "" : String.join(" ", request.ingredients()),
                request.ingredientRatios() == null ? "" : String.join(" ", request.ingredientRatios()),
                nullToEmpty(request.dosageForm()),
                nullToEmpty(request.intendedUse()),
                request.claims() == null ? "" : String.join(" ", request.claims()),
                nullToEmpty(request.manufacturingMethod()),
                nullToEmpty(request.classicalReference()),
                nullToEmpty(request.sourceOfIngredients()),
                nullToEmpty(request.manufacturer()),
                nullToEmpty(request.targetMarket()),
                nullToEmpty(request.country())
        );
    }

    private FormulationRequest canonicalRequest(FormulationRequest request, LanguageMetadata metadata) {
        if (metadata.requestedLanguage() == metadata.processingLanguage()) {
            return request;
        }
        return new FormulationRequest(
                translateInput(request.productName(), metadata),
                translationService.toCanonicalList(request.ingredients(), metadata.requestedLanguage()),
                translateInput(request.dosageForm(), metadata),
                translateInput(request.intendedUse(), metadata),
                translationService.toCanonicalList(request.claims(), metadata.requestedLanguage()),
                translateInput(request.manufacturingMethod(), metadata),
                translateInput(request.classicalReference(), metadata),
                request.traditionalUse(),
                request.commercialIntent(),
                translateInput(request.targetMarket(), metadata),
                translateInput(request.country(), metadata),
                translateInput(request.existingLicense(), metadata),
                translateInput(request.knownClassification(), metadata),
                metadata.processingLanguage(),
                translationService.toCanonicalList(request.ingredientRatios(), metadata.requestedLanguage()),
                translateInput(request.sourceOfIngredients(), metadata),
                translateInput(request.manufacturer(), metadata),
                translateInput(request.countryOfManufacture(), metadata),
                request.documents()
        );
    }

    private String translateInput(String value, LanguageMetadata metadata) {
        if (value == null || metadata.requestedLanguage() == metadata.processingLanguage()) {
            return value;
        }
        return translationService.toCanonical(value, metadata.requestedLanguage(), "field").canonicalText();
    }

    private List<QuestionCitation> mapCitations(List<RagCitation> citations) {
        if (citations == null) return List.of();
        return citations.stream()
                .map(c -> new QuestionCitation(
                        c.document(),
                        c.documentId(),
                        c.page(),
                        c.section(),
                        c.authority(),
                        c.sourceUrl(),
                        c.chunkId()
                ))
                .toList();
    }

    private List<QuestionSource> mapSources(List<RagSource> sources) {
        if (sources == null) return List.of();
        return sources.stream()
                .map(s -> new QuestionSource(s.documentId(), s.score()))
                .toList();
    }

    private List<QuestionCitation> deduplicateCitations(List<QuestionCitation> citations) {
        Map<String, QuestionCitation> map = new LinkedHashMap<>();
        for (QuestionCitation c : citations) {
            String key = c.documentId() + ":" + c.section();
            map.putIfAbsent(key, c);
        }
        return new ArrayList<>(map.values());
    }

    private List<QuestionSource> deduplicateSources(List<QuestionSource> sources) {
        Map<String, QuestionSource> map = new LinkedHashMap<>();
        for (QuestionSource s : sources) {
            map.putIfAbsent(s.documentId(), s);
        }
        return new ArrayList<>(map.values());
    }

    private String extractParenthetical(String text) {
        int start = text.indexOf('(');
        int end = text.indexOf(')');
        if (start != -1 && end != -1 && end > start) {
            return text.substring(start + 1, end).trim();
        }
        return null;
    }

    private boolean containsAny(String text, String... candidates) {
        for (String c : candidates) {
            if (text.contains(c)) return true;
        }
        return false;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String translateReport(String report, LanguageMetadata metadata, String requestId) {
        if (report == null || report.isBlank() || metadata.requestedLanguage() == metadata.processingLanguage()) {
            return report;
        }
        try {
            // Fast path: translate entire report in 1 single call
            return translateText(report, metadata, requestId);
        } catch (Exception e) {
            log.info("whole_report_translation_fallback targetLanguage={} reason={}, chunking report",
                    metadata.requestedLanguage(), e.getMessage());
            try {
                // Fallback: Group sections into 2-3 large chunks instead of 17 individual calls
                String[] sections = report.split("(?m)(?=^## )");
                StringBuilder sb = new StringBuilder();
                StringBuilder batch = new StringBuilder();
                for (String section : sections) {
                    if (section.isBlank()) continue;
                    if (batch.length() + section.length() > 2500 && !batch.isEmpty()) {
                        sb.append(translateText(batch.toString(), metadata, requestId));
                        batch.setLength(0);
                    }
                    batch.append(section);
                }
                if (!batch.isEmpty()) {
                    sb.append(translateText(batch.toString(), metadata, requestId));
                }
                return sb.toString();
            } catch (Exception ex2) {
                log.warn("translation_report_failed targetLanguage={} error={}", metadata.requestedLanguage(), ex2.getMessage());
                return report;
            }
        }
    }

    private String translateText(String value, LanguageMetadata metadata, String requestId) {
        if (value == null || value.isBlank() || metadata.requestedLanguage() == metadata.processingLanguage()) {
            return value;
        }
        try {
            return translationService.fromCanonical(value, metadata, requestId);
        } catch (Exception e) {
            log.warn("translation_text_failed targetLanguage={} error={}", metadata.requestedLanguage(), e.getMessage());
            return value;
        }
    }

    private List<String> translateList(List<String> values, LanguageMetadata metadata, String requestId) {
        if (values == null || values.isEmpty() || metadata.requestedLanguage() == metadata.processingLanguage()) {
            return values == null ? List.of() : values;
        }
        try {
            return translationService.fromCanonicalList(values, metadata, requestId);
        } catch (Exception e) {
            log.warn("translation_list_failed targetLanguage={} error={}", metadata.requestedLanguage(), e.getMessage());
            return values;
        }
    }
}
