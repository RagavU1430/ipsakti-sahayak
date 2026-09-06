package com.ipsakti.ip_sakti_backend.formulation.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProvidedDocument(
        String id,
        String name,
        DocumentType type,
        DocumentStatus status,
        String notes,
        String sourceUrl
) {
    public ProvidedDocument(String id, String name, DocumentType type, DocumentStatus status, String notes) {
        this(id, name, type, status, notes, null);
    }
}
