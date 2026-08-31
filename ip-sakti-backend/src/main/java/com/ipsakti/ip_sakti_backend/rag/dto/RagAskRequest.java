package com.ipsakti.ip_sakti_backend.rag.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RagAskRequest(
        @NotBlank @Size(max = 4000) String question,
        String domain,
        String jurisdiction,
        @JsonProperty("top_k") @Min(1) @Max(20) Integer topK
) {
}
