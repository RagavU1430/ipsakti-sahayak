package com.ipsakti.ip_sakti_backend.api;

import com.ipsakti.ip_sakti_backend.tk.TkOverlapService;
import com.ipsakti.ip_sakti_backend.tk.model.TkOverlapRequest;
import com.ipsakti.ip_sakti_backend.tk.model.TkOverlapResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tk")
public class TkOverlapController {

    private final TkOverlapService tkOverlapService;

    public TkOverlapController(TkOverlapService tkOverlapService) {
        this.tkOverlapService = tkOverlapService;
    }

    @PostMapping("/overlap")
    public ResponseEntity<TkOverlapResponse> analyze(@Valid @RequestBody TkOverlapRequest request) {
        return ResponseEntity.ok(tkOverlapService.analyze(request));
    }
}
