package com.ipsakti.ip_sakti_backend.api;

import com.ipsakti.ip_sakti_backend.question.QuestionService;
import com.ipsakti.ip_sakti_backend.question.model.QuestionRequest;
import com.ipsakti.ip_sakti_backend.question.model.QuestionResponse;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/questions")
public class QuestionController {

    private final QuestionService questionService;

    public QuestionController(QuestionService questionService) {
        this.questionService = questionService;
    }

    @PostMapping
    public ResponseEntity<QuestionResponse> askQuestion(@Valid @RequestBody QuestionRequest request) {
        return ResponseEntity.ok(questionService.answer(request));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}
