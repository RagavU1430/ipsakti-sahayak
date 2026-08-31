package com.ipsakti.ip_sakti_backend.rag.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RagCitation(
        String document,
        @JsonProperty("document_id") String documentId,
        Integer page,
        String section,
        String authority,
        @JsonProperty("source_url") String sourceUrl,
        @JsonProperty("chunk_id") String chunkId
) {
}
