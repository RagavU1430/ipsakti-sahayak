package com.ipsakti.ip_sakti_backend.conversation.dto;

import com.ipsakti.ip_sakti_backend.question.model.Jurisdiction;
import com.ipsakti.ip_sakti_backend.question.model.Language;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConversationMessageRequest(
        @NotBlank @Size(max = 4000) String question,
        Jurisdiction jurisdiction,
        Language language
) {
    public ConversationMessageRequest {
        if (question != null) {
            question = String.join(" ", question.trim().split("\\s+"));
        }
        if (jurisdiction == null) {
            jurisdiction = Jurisdiction.AUTO;
        }
    }
}
