package com.ipsakti.ip_sakti_backend.api;

import com.ipsakti.ip_sakti_backend.regulatory.RegulatoryAnalysisService;
import com.ipsakti.ip_sakti_backend.regulatory.model.RegulatoryAnalysisRequest;
import com.ipsakti.ip_sakti_backend.regulatory.model.RegulatoryAnalysisResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/regulatory")
public class RegulatoryController {

    private final RegulatoryAnalysisService regulatoryAnalysisService;

    public RegulatoryController(RegulatoryAnalysisService regulatoryAnalysisService) {
        this.regulatoryAnalysisService = regulatoryAnalysisService;
    }

    @PostMapping("/analyze")
    public ResponseEntity<RegulatoryAnalysisResponse> analyze(@Valid @RequestBody RegulatoryAnalysisRequest request) {
        return ResponseEntity.ok(regulatoryAnalysisService.analyze(request));
    }
}
