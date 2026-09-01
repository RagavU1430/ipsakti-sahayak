package com.ipsakti.ip_sakti_backend.conversation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateConversationRequest(
        @NotBlank @Size(max = 255) String title
) {
    public UpdateConversationRequest {
        if (title != null) {
            title = String.join(" ", title.trim().split("\\s+"));
        }
    }
}
