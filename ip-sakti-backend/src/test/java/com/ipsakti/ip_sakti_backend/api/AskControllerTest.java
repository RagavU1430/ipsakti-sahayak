package com.ipsakti.ip_sakti_backend.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ipsakti.ip_sakti_backend.config.SecurityConfig;
import com.ipsakti.ip_sakti_backend.exception.RagClientException;
import com.ipsakti.ip_sakti_backend.rag.RagClient;
import com.ipsakti.ip_sakti_backend.rag.dto.RagAskResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AskController.class)
@Import(SecurityConfig.class)
class AskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RagClient ragClient;

    @Test
    void forwardsValidQuestionToRag() throws Exception {
        when(ragClient.ask(any())).thenReturn(new RagAskResponse("Answer", 0.91, false, List.of(), List.of()));

        mockMvc.perform(post("/api/v1/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"What is a trademark?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("Answer"))
                .andExpect(jsonPath("$.answer_source").value("general_fallback"));
    }

    @Test
    void rejectsBlankQuestion() throws Exception {
        mockMvc.perform(post("/api/v1/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void returnsControlledRagFailure() throws Exception {
        when(ragClient.ask(any())).thenThrow(RagClientException.timeout());

        mockMvc.perform(post("/api/v1/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"What is a trademark?\"}"))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.code").value("RAG_TIMEOUT"))
                .andExpect(jsonPath("$.detail").value("The RAG service timed out."));
    }
}
