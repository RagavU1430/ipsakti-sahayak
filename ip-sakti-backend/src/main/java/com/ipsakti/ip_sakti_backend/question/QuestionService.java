package com.ipsakti.ip_sakti_backend.question;

import com.ipsakti.ip_sakti_backend.question.classification.JurisdictionResolver;
import com.ipsakti.ip_sakti_backend.question.classification.QuestionIntentClassifier;
import com.ipsakti.ip_sakti_backend.question.model.AnswerType;
import com.ipsakti.ip_sakti_backend.question.model.Jurisdiction;
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

    public QuestionService(
            RagClient ragClient,
            QuestionIntentClassifier intentClassifier,
            JurisdictionResolver jurisdictionResolver
    ) {
        this.ragClient = ragClient;
        this.intentClassifier = intentClassifier;
        this.jurisdictionResolver = jurisdictionResolver;
    }

    public QuestionResponse answer(QuestionRequest request) {
        long started = System.nanoTime();
        String questionId = UUID.randomUUID().toString();
        QuestionIntent intent = intentClassifier.classify(request.question());
        Jurisdiction jurisdiction = jurisdictionResolver.resolve(request.jurisdiction(), intent, request.question());

        log.info(
                "question_request_received questionId={} intent={} jurisdiction={} language={} questionLength={}",
                questionId,
                intent,
                jurisdiction,
                request.language(),
                request.question().length()
        );

        RagAskRequest ragRequest = new RagAskRequest(
                request.question(),
                intentClassifier.ragDomainFor(intent),
                jurisdictionResolver.ragJurisdictionFor(jurisdiction),
                null
        );
        RagAskResponse ragResponse = ragClient.ask(ragRequest);

        QuestionResponse response = new QuestionResponse(
                ragResponse.answer(),
                mapAnswerType(ragResponse.answerSource()),
                ragResponse.confidence(),
                ragResponse.abstained(),
                jurisdiction,
                request.language(),
                intent,
                mapCitations(ragResponse.citations()),
                mapSources(ragResponse.sources())
        );

        log.info(
                "question_response_ready questionId={} intent={} jurisdiction={} language={} answerType={} confidence={} latencyMs={}",
                questionId,
                intent,
                jurisdiction,
                request.language(),
                response.answerType(),
                response.confidence(),
                Duration.ofNanos(System.nanoTime() - started).toMillis()
        );
        return response;
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
