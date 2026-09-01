package com.ipsakti.ip_sakti_backend.conversation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "message_citations")
public class MessageCitationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "message_id", nullable = false)
    private MessageEntity message;

    @Column(name = "document")
    private String document;

    @Column(name = "document_id")
    private String documentId;

    @Column(name = "page")
    private Integer page;

    @Column(name = "section")
    private String section;

    @Column(name = "authority")
    private String authority;

    @Column(name = "source_url")
    private String sourceUrl;

    @Column(name = "chunk_id")
    private String chunkId;

    @Column(name = "ordinal", nullable = false)
    private Integer ordinal = 0;

    public MessageCitationEntity() {
    }

    public MessageCitationEntity(
            MessageEntity message,
            String document,
            String documentId,
            Integer page,
            String section,
            String authority,
            String sourceUrl,
            String chunkId,
            Integer ordinal
    ) {
        this.message = message;
        this.document = document;
        this.documentId = documentId;
        this.page = page;
        this.section = section;
        this.authority = authority;
        this.sourceUrl = sourceUrl;
        this.chunkId = chunkId;
        this.ordinal = (ordinal != null) ? ordinal : 0;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public MessageEntity getMessage() {
        return message;
    }

    public void setMessage(MessageEntity message) {
        this.message = message;
    }

    public String getDocument() {
        return document;
    }

    public void setDocument(String document) {
        this.document = document;
    }

    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    public Integer getPage() {
        return page;
    }

    public void setPage(Integer page) {
        this.page = page;
    }

    public String getSection() {
        return section;
    }

    public void setSection(String section) {
        this.section = section;
    }

    public String getAuthority() {
        return authority;
    }

    public void setAuthority(String authority) {
        this.authority = authority;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    public String getChunkId() {
        return chunkId;
    }

    public void setChunkId(String chunkId) {
        this.chunkId = chunkId;
    }

    public Integer getOrdinal() {
        return ordinal;
    }

    public void setOrdinal(Integer ordinal) {
        this.ordinal = ordinal;
    }
}
