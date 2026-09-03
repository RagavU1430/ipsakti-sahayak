package com.ipsakti.ip_sakti_backend.tk.model;

import com.ipsakti.ip_sakti_backend.question.model.Language;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TkOverlapRequest(
        @NotBlank @Size(max = 4000) String description,
        Language language
) {
}
