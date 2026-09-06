package com.ipsakti.ip_sakti_backend.voice.dto;

import com.ipsakti.ip_sakti_backend.question.model.AnswerType;
import com.ipsakti.ip_sakti_backend.question.model.Jurisdiction;
import com.ipsakti.ip_sakti_backend.question.model.Language;
import com.ipsakti.ip_sakti_backend.question.model.QuestionCitation;
import com.ipsakti.ip_sakti_backend.question.model.QuestionSource;
import com.ipsakti.ip_sakti_backend.question.routing.QueryDomain;
import java.util.List;

public record VoiceAskResponse(
        String transcript,
        Language language,
        Jurisdiction jurisdiction,
        String answer,
        AnswerType answerType,
        String route,
        QueryDomain domain,
        Double confidence,
        List<QuestionCitation> citations,
        List<QuestionSource> sources,
        Boolean abstained,
        String audioBase64,
        String audioMimeType,
        String status,
        long latencyMs,
        String conversationId,
        String userMessageId,
        String assistantMessageId
) {}
