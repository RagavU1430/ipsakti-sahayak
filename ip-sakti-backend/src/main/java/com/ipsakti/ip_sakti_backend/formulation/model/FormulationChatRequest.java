package com.ipsakti.ip_sakti_backend.formulation.model;

import com.ipsakti.ip_sakti_backend.question.model.Language;
import jakarta.validation.constraints.NotBlank;

public record FormulationChatRequest(
        @NotBlank(message = "Message cannot be blank")
        String message,
        String reportContext,
        Language language
) {}
