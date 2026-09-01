package com.ipsakti.ip_sakti_backend.conversation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record ConversationPageResponse(
        List<ConversationSummaryResponse> items,
        int page,
        int size,
        @JsonProperty("total_elements") long totalElements,
        @JsonProperty("total_pages") int totalPages
) {
}
