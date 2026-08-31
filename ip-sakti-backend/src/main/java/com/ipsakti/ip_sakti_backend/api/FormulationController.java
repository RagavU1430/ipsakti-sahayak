package com.ipsakti.ip_sakti_backend.api;

import com.ipsakti.ip_sakti_backend.formulation.FormulationClassificationService;
import com.ipsakti.ip_sakti_backend.formulation.model.FormulationRequest;
import com.ipsakti.ip_sakti_backend.formulation.model.FormulationResponse;
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

    public FormulationController(FormulationClassificationService classificationService) {
        this.classificationService = classificationService;
    }

    @PostMapping("/classify")
    public ResponseEntity<FormulationResponse> classify(@Valid @RequestBody FormulationRequest request) {
        return ResponseEntity.ok(classificationService.classify(request));
    }
}
