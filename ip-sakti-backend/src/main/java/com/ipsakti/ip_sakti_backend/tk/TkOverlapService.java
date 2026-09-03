package com.ipsakti.ip_sakti_backend.tk;

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
import com.ipsakti.ip_sakti_backend.tk.analysis.TkAssessment;
import com.ipsakti.ip_sakti_backend.tk.analysis.TkEvidenceAnalyzer;
import com.ipsakti.ip_sakti_backend.tk.analysis.TkQueryAnalysis;
import com.ipsakti.ip_sakti_backend.tk.analysis.TkQueryAnalyzer;
import com.ipsakti.ip_sakti_backend.tk.model.TkEvidenceItem;
import com.ipsakti.ip_sakti_backend.tk.model.TkOverlapRequest;
import com.ipsakti.ip_sakti_backend.tk.model.TkOverlapResponse;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TkOverlapService {

    private static final Logger log = LoggerFactory.getLogger(TkOverlapService.class);

    private final RagClient ragClient;
    private final TranslationService translationService;
    private final TkQueryAnalyzer queryAnalyzer;
    private final TkEvidenceAnalyzer evidenceAnalyzer;

    public TkOverlapService(
            RagClient ragClient,
            TranslationService translationService,
            TkQueryAnalyzer queryAnalyzer,
            TkEvidenceAnalyzer evidenceAnalyzer
    ) {
        this.ragClient = ragClient;
        this.translationService = translationService;
        this.queryAnalyzer = queryAnalyzer;
        this.evidenceAnalyzer = evidenceAnalyzer;
    }

    public TkOverlapResponse analyze(TkOverlapRequest request) {
        long started = System.nanoTime();
        String requestId = UUID.randomUUID().toString();
        Language requestedLanguage = request.language() == null ? Language.EN : request.language();
        TranslatedText translated = translationService.toCanonical(request.description(), requestedLanguage, requestId);
        LanguageMetadata metadata = translated.metadata();
        String canonicalDescription = translated.canonicalText();
        TkQueryAnalysis queryAnalysis = queryAnalyzer.analyze(canonicalDescription);

        RagAskResponse ragResponse = ragClient.ask(new RagAskRequest(
                ragQuery(canonicalDescription, queryAnalysis),
                null,
                "INDIA",
                8
        ));
        TkAssessment assessment = evidenceAnalyzer.assess(canonicalDescription, queryAnalysis, ragResponse);

        String explanation = translationService.fromCanonical(assessment.explanation(), metadata, requestId);
        String recommendation = translationService.fromCanonical(assessment.recommendation(), metadata, requestId);
        List<QuestionCitation> citations = mapCitations(ragResponse.citations());
        List<QuestionSource> sources = mapSources(ragResponse.sources());

        TkOverlapResponse response = new TkOverlapResponse(
                assessment.classification(),
                assessment.confidence(),
                assessment.overlapTypes(),
                explanation,
                evidenceItems(ragResponse),
                recommendation,
                assessment.abstained() ? List.of() : citations,
                assessment.abstained() ? List.of() : sources,
                assessment.abstained(),
                metadata.requestedLanguage(),
                metadata.detectedLanguage(),
                metadata.processingLanguage()
        );

        log.info(
                "tk_overlap_ready requestId={} classification={} confidence={} overlapTypes={} abstained={} requestedLanguage={} detectedLanguage={} processingLanguage={} latencyMs={}",
                requestId,
                response.classification(),
                response.confidence(),
                response.overlapTypes().size(),
                response.abstained(),
                response.language(),
                response.detectedLanguage(),
                response.processingLanguage(),
                Duration.ofNanos(System.nanoTime() - started).toMillis()
        );
        return response;
    }

    private String ragQuery(String canonicalDescription, TkQueryAnalysis analysis) {
        return "Traditional knowledge overlap assessment under Section 3(p). "
                + "Does Section 3(p) traditional knowledge or known-properties patentability screening apply to this "
                + "Indian Ayurveda, herbal, biological-resource, or traditional-use formulation description? "
                + "Answer only with authoritative corpus evidence and citations. Description: "
                + canonicalDescription
                + ". Ingredients or resources: " + String.join(", ", analysis.ingredients())
                + ". Traditional-use indicators: " + String.join(", ", analysis.traditionalUseTerms())
                + ". TK/legal indicators: " + String.join(", ", analysis.tkIndicators());
    }

    private List<TkEvidenceItem> evidenceItems(RagAskResponse ragResponse) {
        List<RagCitation> citations = ragResponse.citations() == null ? List.of() : ragResponse.citations();
        List<RagSource> sources = ragResponse.sources() == null ? List.of() : ragResponse.sources();
        return citations.stream()
                .map(citation -> new TkEvidenceItem(
                        citation.document(),
                        citation.documentId(),
                        citation.page(),
                        citation.section(),
                        citation.authority(),
                        citation.sourceUrl(),
                        citation.chunkId(),
                        scoreFor(citation.documentId(), sources)
                ))
                .toList();
    }

    private Double scoreFor(String documentId, List<RagSource> sources) {
        if (documentId == null) {
            return null;
        }
        return sources.stream()
                .filter(source -> documentId.equals(source.documentId()))
                .map(RagSource::score)
                .findFirst()
                .orElse(null);
    }

    private List<QuestionCitation> mapCitations(List<RagCitation> citations) {
        if (citations == null) {
            return List.of();
        }
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
        if (sources == null) {
            return List.of();
        }
        return sources.stream()
                .map(source -> new QuestionSource(source.documentId(), source.score()))
                .toList();
    }
}
