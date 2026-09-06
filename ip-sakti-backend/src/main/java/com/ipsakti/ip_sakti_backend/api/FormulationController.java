package com.ipsakti.ip_sakti_backend.api;

import com.ipsakti.ip_sakti_backend.formulation.AyurvedaProductReadinessService;
import com.ipsakti.ip_sakti_backend.formulation.FormulationClassificationService;
import com.ipsakti.ip_sakti_backend.formulation.model.FormulationRequest;
import com.ipsakti.ip_sakti_backend.formulation.model.FormulationResponse;
import com.ipsakti.ip_sakti_backend.formulation.model.ProductReadinessResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/formulations")
public class FormulationController {

    private final FormulationClassificationService classificationService;
    private final AyurvedaProductReadinessService readinessService;
    private final com.ipsakti.ip_sakti_backend.formulation.FormulationChatService chatService;

    public FormulationController(
            FormulationClassificationService classificationService,
            AyurvedaProductReadinessService readinessService,
            com.ipsakti.ip_sakti_backend.formulation.FormulationChatService chatService
    ) {
        this.classificationService = classificationService;
        this.readinessService = readinessService;
        this.chatService = chatService;
    }

    @PostMapping("/classify")
    public ResponseEntity<FormulationResponse> classify(@Valid @RequestBody FormulationRequest request) {
        return ResponseEntity.ok(classificationService.classify(request));
    }

    @PostMapping("/analyze")
    public ResponseEntity<ProductReadinessResponse> analyze(@Valid @RequestBody FormulationRequest request) {
        return ResponseEntity.ok(readinessService.analyze(request));
    }

    @PostMapping("/chat")
    public ResponseEntity<com.ipsakti.ip_sakti_backend.formulation.model.FormulationChatResponse> chat(
            @Valid @RequestBody com.ipsakti.ip_sakti_backend.formulation.model.FormulationChatRequest request
    ) {
        return ResponseEntity.ok(chatService.chat(request));
    }
}
