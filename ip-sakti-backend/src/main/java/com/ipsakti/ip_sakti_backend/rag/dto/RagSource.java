package com.ipsakti.ip_sakti_backend.rag.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RagSource(
        @JsonProperty("document_id") String documentId,
        Double score
) {
}
