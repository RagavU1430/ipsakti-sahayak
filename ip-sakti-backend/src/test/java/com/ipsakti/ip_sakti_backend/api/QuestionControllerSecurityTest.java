package com.ipsakti.ip_sakti_backend.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ipsakti.ip_sakti_backend.config.SecurityConfig;
import com.ipsakti.ip_sakti_backend.question.QuestionService;
import com.ipsakti.ip_sakti_backend.question.classification.JurisdictionResolver;
import com.ipsakti.ip_sakti_backend.question.classification.QuestionIntentClassifier;
import com.ipsakti.ip_sakti_backend.rag.RagClient;
import com.ipsakti.ip_sakti_backend.rag.dto.RagAskResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(QuestionController.class)
@Import({SecurityConfig.class, QuestionService.class, QuestionIntentClassifier.class, JurisdictionResolver.class})
@TestPropertySource(properties = {
        "app.security.mode=prod",
        "app.security.api-key=test-api-key"
})
class QuestionControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RagClient ragClient;

    @Test
    void rejectsQuestionWithoutApiKeyInProdMode() throws Exception {
        mockMvc.perform(post("/api/v1/questions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"What is a patent?\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void acceptsQuestionWithApiKeyInProdMode() throws Exception {
        when(ragClient.ask(any())).thenReturn(new RagAskResponse("Answer", 0.8, false, List.of(), List.of()));

        mockMvc.perform(post("/api/v1/questions")
                        .header("X-API-Key", "test-api-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"What is a patent?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("Answer"));
    }
}
