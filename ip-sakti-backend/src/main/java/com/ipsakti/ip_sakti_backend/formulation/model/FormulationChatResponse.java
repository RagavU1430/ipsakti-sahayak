package com.ipsakti.ip_sakti_backend.formulation.model;

import com.ipsakti.ip_sakti_backend.question.model.Language;
import java.util.List;

public record FormulationChatResponse(
        String reply,
        Language language,
        List<String> suggestedQuestions
) {}
