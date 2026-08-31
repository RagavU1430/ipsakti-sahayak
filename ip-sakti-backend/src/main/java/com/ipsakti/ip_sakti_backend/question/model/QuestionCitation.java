package com.ipsakti.ip_sakti_backend.question.model;

public record QuestionCitation(
        String document,
        String documentId,
        Integer page,
        String section,
        String authority,
        String sourceUrl,
        String chunkId
) {
}
