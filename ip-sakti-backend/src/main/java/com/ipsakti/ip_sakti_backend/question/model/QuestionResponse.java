package com.ipsakti.ip_sakti_backend.question.model;

import java.util.List;

public record QuestionResponse(
        String answer,
        AnswerType answerType,
        Double confidence,
        Boolean abstained,
        Jurisdiction jurisdiction,
        Language language,
        QuestionIntent intent,
        List<QuestionCitation> citations,
        List<QuestionSource> sources
) {
}
