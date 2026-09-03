package com.ipsakti.ip_sakti_backend.tk.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TkEvidenceItem(
        String document,
        @JsonProperty("document_id") String documentId,
        Integer page,
        String section,
        String authority,
        @JsonProperty("source_url") String sourceUrl,
        @JsonProperty("chunk_id") String chunkId,
        Double score
) {
}
