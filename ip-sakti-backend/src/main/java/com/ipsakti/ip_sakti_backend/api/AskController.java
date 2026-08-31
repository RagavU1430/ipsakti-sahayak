package com.ipsakti.ip_sakti_backend.api;

import com.ipsakti.ip_sakti_backend.rag.RagClient;
import com.ipsakti.ip_sakti_backend.rag.dto.RagAskRequest;
import com.ipsakti.ip_sakti_backend.rag.dto.RagAskResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class AskController {

    private static final Logger log = LoggerFactory.getLogger(AskController.class);

    private final RagClient ragClient;

    public AskController(RagClient ragClient) {
        this.ragClient = ragClient;
    }

    @PostMapping("/ask")
    public ResponseEntity<RagAskResponse> ask(@Valid @RequestBody RagAskRequest request) {
        log.info("backend_question_received questionLength={}", request.question().length());
        return ResponseEntity.ok(ragClient.ask(request));
    }
}
