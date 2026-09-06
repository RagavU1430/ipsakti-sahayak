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

    public FormulationController(
            FormulationClassificationService classificationService,
            AyurvedaProductReadinessService readinessService
    ) {
        this.classificationService = classificationService;
        this.readinessService = readinessService;
    }

    @PostMapping("/classify")
    public ResponseEntity<FormulationResponse> classify(@Valid @RequestBody FormulationRequest request) {
        return ResponseEntity.ok(classificationService.classify(request));
    }

    @PostMapping("/analyze")
    public ResponseEntity<ProductReadinessResponse> analyze(@Valid @RequestBody FormulationRequest request) {
        return ResponseEntity.ok(readinessService.analyze(request));
    }
}
