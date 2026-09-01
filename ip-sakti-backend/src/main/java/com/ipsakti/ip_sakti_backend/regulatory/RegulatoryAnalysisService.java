package com.ipsakti.ip_sakti_backend.regulatory;

import com.ipsakti.ip_sakti_backend.multilingual.LanguageMetadata;
import com.ipsakti.ip_sakti_backend.multilingual.TranslatedText;
import com.ipsakti.ip_sakti_backend.multilingual.TranslationService;
import com.ipsakti.ip_sakti_backend.question.model.Jurisdiction;
import com.ipsakti.ip_sakti_backend.regulatory.engine.AbsAnalysisService;
import com.ipsakti.ip_sakti_backend.regulatory.engine.GratkAnalysisService;
import com.ipsakti.ip_sakti_backend.regulatory.engine.RegulatoryJurisdictionRouter;
import com.ipsakti.ip_sakti_backend.regulatory.engine.Section3eAnalysisService;
import com.ipsakti.ip_sakti_backend.regulatory.engine.Section3pAnalysisService;
import com.ipsakti.ip_sakti_backend.regulatory.model.RegulatoryAnalysisRequest;
import com.ipsakti.ip_sakti_backend.regulatory.model.RegulatoryAnalysisResponse;
import com.ipsakti.ip_sakti_backend.regulatory.model.RegulatoryEngineResult;
import com.ipsakti.ip_sakti_backend.regulatory.model.RegulatoryStatus;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class RegulatoryAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(RegulatoryAnalysisService.class);

    private final RegulatoryJurisdictionRouter jurisdictionRouter;
    private final Section3pAnalysisService section3p;
    private final Section3eAnalysisService section3e;
    private final AbsAnalysisService abs;
    private final GratkAnalysisService gratk;
    private final TranslationService translationService;

    public RegulatoryAnalysisService(RegulatoryJurisdictionRouter jurisdictionRouter, Section3pAnalysisService section3p,
                                     Section3eAnalysisService section3e, AbsAnalysisService abs, GratkAnalysisService gratk,
                                     TranslationService translationService) {
        this.jurisdictionRouter = jurisdictionRouter;
        this.section3p = section3p;
        this.section3e = section3e;
        this.abs = abs;
        this.gratk = gratk;
        this.translationService = translationService;
    }

    public RegulatoryAnalysisResponse analyze(RegulatoryAnalysisRequest request) {
        long started = System.nanoTime();
        String requestId = UUID.randomUUID().toString();
        TranslatedText translatedText = translationService.toCanonical(request.combinedText(), request.language(), requestId);
        LanguageMetadata languageMetadata = translatedText.metadata();
        RegulatoryAnalysisRequest canonicalRequest = canonicalRequest(request, languageMetadata);
        Jurisdiction jurisdiction = jurisdictionRouter.resolve(canonicalRequest);
        if (jurisdictionRouter.ambiguous(jurisdiction)) {
            return new RegulatoryAnalysisResponse(
                    Jurisdiction.AUTO,
                    RegulatoryStatus.INSUFFICIENT_EVIDENCE,
                    List.of(),
                    0.2,
                    true,
                    translationService.fromCanonicalList(List.of("Which jurisdiction are you seeking guidance for: India or international?"), languageMetadata, requestId),
                    translationService.fromCanonical("Jurisdiction could not be safely inferred, so regulatory analysis was not run.", languageMetadata, requestId),
                    languageMetadata.requestedLanguage(),
                    languageMetadata.detectedLanguage(),
                    languageMetadata.processingLanguage()
            );
        }

        List<RegulatoryEngineResult> engines = List.of(
                section3p.analyze(canonicalRequest, jurisdiction),
                section3e.analyze(canonicalRequest, jurisdiction),
                abs.analyze(canonicalRequest, jurisdiction),
                gratk.analyze(canonicalRequest, jurisdiction)
        );
        boolean needsClarification = engines.stream().anyMatch(result ->
                result.status() == RegulatoryStatus.INSUFFICIENT_EVIDENCE
                        || result.reason().toLowerCase().contains("conflicting")
                        || result.reason().toLowerCase().contains("incomplete")
                        || result.considerations().stream()
                        .anyMatch(consideration -> consideration.toLowerCase().contains("incomplete")));
        RegulatoryStatus overall = overallStatus(engines);
        double confidence = Math.round(engines.stream().mapToDouble(RegulatoryEngineResult::confidence).average().orElse(0.0) * 10000.0) / 10000.0;
        RegulatoryAnalysisResponse response = new RegulatoryAnalysisResponse(
                jurisdiction,
                overall,
                translateEngines(engines, languageMetadata, requestId),
                confidence,
                needsClarification,
                needsClarification
                        ? translationService.fromCanonicalList(List.of("Please clarify any missing or conflicting traditional-knowledge, biological-resource, origin, or technical-effect facts."), languageMetadata, requestId)
                        : List.of(),
                translationService.fromCanonical("This is an evidence-backed decision-support summary, not final legal advice.", languageMetadata, requestId),
                languageMetadata.requestedLanguage(),
                languageMetadata.detectedLanguage(),
                languageMetadata.processingLanguage()
        );
        log.info("regulatory_analysis_ready requestId={} jurisdiction={} overallStatus={} confidence={} engineCount={} latencyMs={}",
                requestId, jurisdiction, overall, confidence, engines.size(), Duration.ofNanos(System.nanoTime() - started).toMillis());
        return response;
    }

    private List<RegulatoryEngineResult> translateEngines(
            List<RegulatoryEngineResult> engines,
            LanguageMetadata metadata,
            String requestId
    ) {
        if (metadata.requestedLanguage() == metadata.processingLanguage()) {
            return engines;
        }
        return engines.stream()
                .map(result -> new RegulatoryEngineResult(
                        result.engine(),
                        result.status(),
                        result.confidence(),
                        translationService.fromCanonical(result.reason(), metadata, requestId),
                        translationService.fromCanonicalList(result.considerations(), metadata, requestId),
                        result.resourceType(),
                        result.citations(),
                        result.sources()
                ))
                .toList();
    }

    private RegulatoryAnalysisRequest canonicalRequest(RegulatoryAnalysisRequest request, LanguageMetadata metadata) {
        if (metadata.requestedLanguage() == metadata.processingLanguage()) {
            return request;
        }
        return new RegulatoryAnalysisRequest(
                translateInput(request.productName(), metadata),
                translationService.toCanonicalList(request.ingredients(), metadata.requestedLanguage()),
                translateInput(request.dosageForm(), metadata),
                translateInput(request.intendedUse(), metadata),
                translationService.toCanonicalList(request.claims(), metadata.requestedLanguage()),
                request.traditionalKnowledge(),
                translateInput(request.classicalReference(), metadata),
                request.biologicalResources(),
                translateInput(request.resourceOrigin(), metadata),
                translateInput(request.targetMarket(), metadata),
                request.jurisdiction(),
                request.formulationNovelty(),
                request.knownIngredients(),
                request.synergisticEffectClaimed(),
                request.geneticResources(),
                metadata.processingLanguage()
        );
    }

    private String translateInput(String value, LanguageMetadata metadata) {
        if (value == null || metadata.requestedLanguage() == metadata.processingLanguage()) {
            return value;
        }
        return translationService.toCanonical(value, metadata.requestedLanguage(), "field").canonicalText();
    }

    private RegulatoryStatus overallStatus(List<RegulatoryEngineResult> engines) {
        if (engines.stream().anyMatch(result -> result.status() == RegulatoryStatus.REVIEW_RECOMMENDED)) {
            return RegulatoryStatus.REVIEW_RECOMMENDED;
        }
        if (engines.stream().anyMatch(result -> result.status() == RegulatoryStatus.POTENTIALLY_APPLICABLE)) {
            return RegulatoryStatus.POTENTIALLY_APPLICABLE;
        }
        return engines.stream()
                .map(RegulatoryEngineResult::status)
                .max(Comparator.comparingInt(Enum::ordinal))
                .orElse(RegulatoryStatus.INSUFFICIENT_EVIDENCE);
    }
}
