package com.ipsakti.ip_sakti_backend.conversation.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "messages")
public class MessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private ConversationEntity conversation;

    @Column(name = "role", nullable = false)
    private String role; // "user", "assistant", "system"

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "response_type")
    private String responseType;

    @Column(name = "confidence")
    private Double confidence;

    @Column(name = "abstained")
    private Boolean abstained;

    @Column(name = "jurisdiction")
    private String jurisdiction;

    @Column(name = "language")
    private String language;

    @Column(name = "detected_language")
    private String detectedLanguage;

    @Column(name = "processing_language")
    private String processingLanguage;

    @Column(name = "intent")
    private String intent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "message", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("ordinal ASC")
    private List<MessageCitationEntity> citations = new ArrayList<>();

    @OneToMany(mappedBy = "message", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("ordinal ASC")
    private List<MessageSourceEntity> sources = new ArrayList<>();

    public MessageEntity() {
    }

    public MessageEntity(
            ConversationEntity conversation,
            String role,
            String content,
            String responseType,
            Double confidence,
            Boolean abstained,
            String jurisdiction,
            String language,
            String detectedLanguage,
            String processingLanguage,
            String intent
    ) {
        this.conversation = conversation;
        this.role = role;
        this.content = content;
        this.responseType = responseType;
        this.confidence = confidence;
        this.abstained = abstained;
        this.jurisdiction = jurisdiction;
        this.language = language;
        this.detectedLanguage = detectedLanguage;
        this.processingLanguage = processingLanguage;
        this.intent = intent;
    }

    public static MessageEntity userMessage(ConversationEntity conversation, String content, String jurisdiction, String language) {
        return new MessageEntity(conversation, "user", content, null, null, null, jurisdiction, language, null, null, null);
    }

    public static MessageEntity assistantMessage(
            ConversationEntity conversation,
            String content,
            String responseType,
            Double confidence,
            Boolean abstained,
            String jurisdiction,
            String language,
            String detectedLanguage,
            String processingLanguage,
            String intent
    ) {
        return new MessageEntity(
                conversation,
                "assistant",
                content,
                responseType,
                confidence,
                abstained,
                jurisdiction,
                language,
                detectedLanguage,
                processingLanguage,
                intent
        );
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public ConversationEntity getConversation() {
        return conversation;
    }

    public void setConversation(ConversationEntity conversation) {
        this.conversation = conversation;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getResponseType() {
        return responseType;
    }

    public void setResponseType(String responseType) {
        this.responseType = responseType;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public Boolean getAbstained() {
        return abstained;
    }

    public void setAbstained(Boolean abstained) {
        this.abstained = abstained;
    }

    public String getJurisdiction() {
        return jurisdiction;
    }

    public void setJurisdiction(String jurisdiction) {
        this.jurisdiction = jurisdiction;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getDetectedLanguage() {
        return detectedLanguage;
    }

    public void setDetectedLanguage(String detectedLanguage) {
        this.detectedLanguage = detectedLanguage;
    }

    public String getProcessingLanguage() {
        return processingLanguage;
    }

    public void setProcessingLanguage(String processingLanguage) {
        this.processingLanguage = processingLanguage;
    }

    public String getIntent() {
        return intent;
    }

    public void setIntent(String intent) {
        this.intent = intent;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public List<MessageCitationEntity> getCitations() {
        return citations;
    }

    public void setCitations(List<MessageCitationEntity> citations) {
        this.citations = citations;
    }

    public List<MessageSourceEntity> getSources() {
        return sources;
    }

    public void setSources(List<MessageSourceEntity> sources) {
        this.sources = sources;
    }

    public void addCitation(MessageCitationEntity citation) {
        citations.add(citation);
        citation.setMessage(this);
    }

    public void addSource(MessageSourceEntity source) {
        sources.add(source);
        source.setMessage(this);
    }
}
