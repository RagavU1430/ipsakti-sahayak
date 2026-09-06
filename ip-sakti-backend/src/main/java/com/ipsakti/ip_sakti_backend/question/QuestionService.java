package com.ipsakti.ip_sakti_backend.question;

import com.ipsakti.ip_sakti_backend.multilingual.LanguageMetadata;
import com.ipsakti.ip_sakti_backend.multilingual.TranslatedText;
import com.ipsakti.ip_sakti_backend.multilingual.TranslationService;
import com.ipsakti.ip_sakti_backend.question.classification.JurisdictionResolver;
import com.ipsakti.ip_sakti_backend.question.classification.QuestionIntentClassifier;
import com.ipsakti.ip_sakti_backend.question.model.AnswerType;
import com.ipsakti.ip_sakti_backend.question.model.Jurisdiction;
import com.ipsakti.ip_sakti_backend.question.model.Language;
import com.ipsakti.ip_sakti_backend.question.model.QuestionCitation;
import com.ipsakti.ip_sakti_backend.question.model.QuestionIntent;
import com.ipsakti.ip_sakti_backend.question.model.QuestionRequest;
import com.ipsakti.ip_sakti_backend.question.model.QuestionResponse;
import com.ipsakti.ip_sakti_backend.question.model.QuestionSource;
import com.ipsakti.ip_sakti_backend.rag.RagClient;
import com.ipsakti.ip_sakti_backend.rag.dto.RagAnswerSource;
import com.ipsakti.ip_sakti_backend.rag.dto.RagAskRequest;
import com.ipsakti.ip_sakti_backend.rag.dto.RagAskResponse;
import com.ipsakti.ip_sakti_backend.rag.dto.RagCitation;
import com.ipsakti.ip_sakti_backend.rag.dto.RagSource;
import com.ipsakti.ip_sakti_backend.question.general.GeneralLlmProvider;
import com.ipsakti.ip_sakti_backend.question.routing.QueryDomain;
import com.ipsakti.ip_sakti_backend.question.routing.QueryRoute;
import com.ipsakti.ip_sakti_backend.question.routing.QueryRouter;
import com.ipsakti.ip_sakti_backend.question.routing.RoutingContext;
import com.ipsakti.ip_sakti_backend.question.routing.RoutingDecision;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class QuestionService {

    private static final Logger log = LoggerFactory.getLogger(QuestionService.class);

    private final RagClient ragClient;
    private final QuestionIntentClassifier intentClassifier;
    private final JurisdictionResolver jurisdictionResolver;
    private final TranslationService translationService;
    private final QueryRouter queryRouter;
    private final GeneralLlmProvider generalLlmProvider;

    public QuestionService(
            RagClient ragClient,
            QuestionIntentClassifier intentClassifier,
            JurisdictionResolver jurisdictionResolver,
            TranslationService translationService,
            QueryRouter queryRouter,
            GeneralLlmProvider generalLlmProvider
    ) {
        this.ragClient = ragClient;
        this.intentClassifier = intentClassifier;
        this.jurisdictionResolver = jurisdictionResolver;
        this.translationService = translationService;
        this.queryRouter = queryRouter;
        this.generalLlmProvider = generalLlmProvider;
    }

    public QuestionResponse answer(QuestionRequest request) {
        return answer(request, RoutingContext.empty());
    }

    public QuestionResponse answer(QuestionRequest request, RoutingContext context) {
        long started = System.nanoTime();
        String questionId = UUID.randomUUID().toString();
        TranslatedText canonicalQuestion = translationService.toCanonical(request.question(), request.language(), questionId);
        LanguageMetadata languageMetadata = canonicalQuestion.metadata();
        QuestionIntent intent = intentClassifier.classify(canonicalQuestion.canonicalText());
        Jurisdiction jurisdiction = jurisdictionResolver.resolve(request.jurisdiction(), intent, canonicalQuestion.canonicalText());
        long routingStarted = System.nanoTime();
        RoutingDecision routing = queryRouter.route(canonicalQuestion.canonicalText(), languageMetadata.requestedLanguage(), jurisdiction, context);
        long routingMs = Duration.ofNanos(System.nanoTime() - routingStarted).toMillis();

        log.info(
                "question_request_received questionId={} intent={} jurisdiction={} requestedLanguage={} detectedLanguage={} processingLanguage={} questionLength={}",
                questionId,
                intent,
                jurisdiction,
                languageMetadata.requestedLanguage(),
                languageMetadata.detectedLanguage(),
                languageMetadata.processingLanguage(),
                request.question().length()
        );

        log.info("question_routed questionId={} route={} domain={} reason={} routingConfidence={} routingLatencyMs={}",
                questionId, routing.route(), routing.domain(), routing.reason(), routing.confidence(), routingMs);

        if (routing.route() == QueryRoute.AMBIGUOUS || routing.route() == QueryRoute.UNSUPPORTED) {
            String canonicalAnswer = routing.route() == QueryRoute.AMBIGUOUS
                    ? "Could you clarify whether you want a general explanation or an answer based on authoritative IP, legal, regulatory, or document sources?"
                    : "I cannot help with that request. You can ask a general question or an IP, legal, regulatory, or document-grounded question.";
            String answer = translationService.fromCanonical(canonicalAnswer, languageMetadata, questionId);
            return new QuestionResponse(answer, AnswerType.GENERAL_FALLBACK, routing.route().name(), routing.domain(), routing.reason(),
                    null, false, jurisdiction, languageMetadata.requestedLanguage(), languageMetadata.detectedLanguage(),
                    languageMetadata.processingLanguage(), intent, List.of(), List.of());
        }

        if (routing.route() == QueryRoute.GENERAL) {
            String canonicalAnswer = generalLlmProvider.answer(canonicalQuestion.canonicalText());
            GuardrailDecision guardrail = evaluateGuardrail(canonicalAnswer, routing.domain(), intent);
            if (guardrail.requiresUpgrade()) {
                log.info("question_route_upgraded_to_domain_rag questionId={} reason=guardrail_authoritative_evidence_required domain={}",
                        questionId, guardrail.inferredDomain());
                RoutingDecision upgradedRouting = new RoutingDecision(
                        QueryRoute.DOMAIN_RAG,
                        guardrail.inferredDomain(),
                        0.95,
                        guardrail.reason(),
                        true,
                        false
                );
                return executeRagPipeline(upgradedRouting, canonicalQuestion, languageMetadata, jurisdiction, intent, questionId, started);
            }
            String answer = translationService.fromCanonical(canonicalAnswer, languageMetadata, questionId);
            QuestionResponse response = new QuestionResponse(answer, AnswerType.GENERAL_FALLBACK, "GENERAL", null, routing.reason(),
                    null, false, jurisdiction, languageMetadata.requestedLanguage(), languageMetadata.detectedLanguage(),
                    languageMetadata.processingLanguage(), QuestionIntent.GENERAL, List.of(), List.of());
            log.info("question_response_ready questionId={} route=GENERAL provider={} confidence=null latencyMs={}",
                    questionId, generalLlmProvider.providerName(), Duration.ofNanos(System.nanoTime() - started).toMillis());
            return response;
        }

        return executeRagPipeline(routing, canonicalQuestion, languageMetadata, jurisdiction, intent, questionId, started);
    }

    public record GuardrailDecision(
            boolean requiresUpgrade,
            QueryDomain inferredDomain,
            com.ipsakti.ip_sakti_backend.question.routing.RoutingReason reason
    ) {
        public static GuardrailDecision stayGeneral() {
            return new GuardrailDecision(false, null, null);
        }

        public static GuardrailDecision upgrade(QueryDomain domain) {
            return new GuardrailDecision(true, domain, com.ipsakti.ip_sakti_backend.question.routing.RoutingReason.DOMAIN_AUTHORITY_REQUIRED);
        }
    }

    private GuardrailDecision evaluateGuardrail(String text, QueryDomain existingDomain, QuestionIntent intent) {
        if (isGuardrailRagRequired(text)) {
            QueryDomain domain = existingDomain != null ? existingDomain : domainFromIntent(intent);
            return GuardrailDecision.upgrade(domain);
        }
        return GuardrailDecision.stayGeneral();
    }

    private boolean isGuardrailRagRequired(String text) {
        if (text == null || text.isBlank()) return false;
        String lower = text.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("authoritative domain evidence")
                || lower.contains("must be routed to rag")
                || lower.contains("routed to rag")
                || lower.contains("requires authoritative");
    }

    private QueryDomain domainFromIntent(QuestionIntent intent) {
        if (intent == null) return null;
        return switch (intent) {
            case PATENT -> QueryDomain.PATENT;
            case TRADEMARK -> QueryDomain.TRADEMARK;
            case COPYRIGHT -> QueryDomain.COPYRIGHT;
            case DESIGN -> QueryDomain.INDUSTRIAL_DESIGN;
            case GI -> QueryDomain.GEOGRAPHICAL_INDICATION;
            case BIODIVERSITY_ABS -> QueryDomain.ABS;
            case AYURVEDA_REGULATION -> QueryDomain.AYURVEDA;
            case INTERNATIONAL_IP -> QueryDomain.INTERNATIONAL_IP;
            case IP_GENERAL, PLANT_VARIETY -> QueryDomain.IP;
            default -> null;
        };
    }

    private QuestionResponse executeRagPipeline(
            RoutingDecision routing,
            TranslatedText canonicalQuestion,
            LanguageMetadata languageMetadata,
            Jurisdiction jurisdiction,
            QuestionIntent intent,
            String questionId,
            long started
    ) {
        RagAskRequest ragRequest = new RagAskRequest(
                canonicalQuestion.canonicalText(),
                ragDomain(routing, intent),
                jurisdictionResolver.ragJurisdictionFor(jurisdiction),
                null
        );
        RagAskResponse ragResponse = ragClient.ask(ragRequest);
        String answer = translationService.fromCanonical(ragResponse.answer(), languageMetadata, questionId);

        String routeName = routing.route() != null ? routing.route().name() : "DOMAIN_RAG";

        QuestionResponse response = new QuestionResponse(
                answer,
                mapAnswerType(ragResponse.answerSource()),
                routeName,
                routing.domain(),
                routing.reason(),
                ragResponse.confidence(),
                ragResponse.abstained(),
                jurisdiction,
                languageMetadata.requestedLanguage(),
                languageMetadata.detectedLanguage(),
                languageMetadata.processingLanguage(),
                intent,
                mapCitations(ragResponse.citations()),
                mapSources(ragResponse.sources())
        );

        log.info(
                "question_response_ready questionId={} intent={} jurisdiction={} requestedLanguage={} detectedLanguage={} processingLanguage={} answerType={} confidence={} latencyMs={}",
                questionId,
                intent,
                jurisdiction,
                languageMetadata.requestedLanguage(),
                languageMetadata.detectedLanguage(),
                languageMetadata.processingLanguage(),
                response.answerType(),
                response.confidence(),
                Duration.ofNanos(System.nanoTime() - started).toMillis()
        );
        return response;
    }

    private String ragDomain(RoutingDecision routing, QuestionIntent intent) {
        if (routing.domain() == null) return intentClassifier.ragDomainFor(intent);
        return switch (routing.domain()) {
            case PATENT -> "PATENT";
            case TRADEMARK -> "TRADEMARK";
            case COPYRIGHT -> "COPYRIGHT";
            case GEOGRAPHICAL_INDICATION -> "GI";
            case INDUSTRIAL_DESIGN -> "DESIGN";
            case BIODIVERSITY, ABS -> "ABS";
            case AYURVEDA, FORMULATION -> "AYURVEDA";
            case GRATK, INTERNATIONAL_IP -> "INTERNATIONAL";
            case TRADITIONAL_KNOWLEDGE -> "PATENT";
            case TRADE_SECRET -> "PATENT";
            case IP, INDIA_IP_LAW, REGULATORY, GOVERNMENT_POLICY, DOCUMENT_GROUNDED -> intentClassifier.ragDomainFor(intent);
        };
    }

    private AnswerType mapAnswerType(RagAnswerSource answerSource) {
        return switch (answerSource) {
            case RAG_GROUNDED -> AnswerType.RAG_GROUNDED;
            case ABSTAINED -> AnswerType.ABSTAINED;
            case GENERAL_FALLBACK -> AnswerType.GENERAL_FALLBACK;
        };
    }

    private List<QuestionCitation> mapCitations(List<RagCitation> citations) {
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
        return sources.stream()
                .map(source -> new QuestionSource(source.documentId(), source.score()))
                .toList();
    }
}
