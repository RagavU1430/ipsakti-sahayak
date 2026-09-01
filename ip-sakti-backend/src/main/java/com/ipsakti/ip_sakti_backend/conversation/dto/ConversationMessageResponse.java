package com.ipsakti.ip_sakti_backend.conversation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ipsakti.ip_sakti_backend.question.model.Jurisdiction;
import com.ipsakti.ip_sakti_backend.question.model.Language;
import com.ipsakti.ip_sakti_backend.question.model.QuestionCitation;
import com.ipsakti.ip_sakti_backend.question.model.QuestionIntent;
import com.ipsakti.ip_sakti_backend.question.model.QuestionSource;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ConversationMessageResponse(
        @JsonProperty("conversation_id") UUID conversationId,
        @JsonProperty("message_id") UUID messageId,
        @JsonProperty("user_message_id") UUID userMessageId,
        String answer,
        @JsonProperty("response_type") String responseType,
        Double confidence,
        Boolean abstained,
        Jurisdiction jurisdiction,
        Language language,
        @JsonProperty("detected_language") Language detectedLanguage,
        @JsonProperty("processing_language") Language processingLanguage,
        QuestionIntent intent,
        List<QuestionCitation> citations,
        List<QuestionSource> sources,
        @JsonProperty("created_at") Instant createdAt
) {
}
