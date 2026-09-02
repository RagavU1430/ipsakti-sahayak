package com.ipsakti.ip_sakti_backend.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ipsakti.ip_sakti_backend.config.SecurityConfig;
import com.ipsakti.ip_sakti_backend.exception.TranslationException;
import com.ipsakti.ip_sakti_backend.exception.RagClientException;
import com.ipsakti.ip_sakti_backend.multilingual.TranslationProvider;
import com.ipsakti.ip_sakti_backend.multilingual.TranslationService;
import com.ipsakti.ip_sakti_backend.question.QuestionService;
import com.ipsakti.ip_sakti_backend.question.classification.JurisdictionResolver;
import com.ipsakti.ip_sakti_backend.question.classification.QuestionIntentClassifier;
import com.ipsakti.ip_sakti_backend.rag.RagClient;
import com.ipsakti.ip_sakti_backend.rag.dto.RagAskResponse;
import com.ipsakti.ip_sakti_backend.rag.dto.RagCitation;
import com.ipsakti.ip_sakti_backend.rag.dto.RagSource;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(QuestionController.class)
@Import({SecurityConfig.class, QuestionService.class, QuestionIntentClassifier.class, JurisdictionResolver.class, TranslationService.class})
class QuestionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RagClient ragClient;

    @MockitoBean
    private TranslationProvider translationProvider;

    @Test
    void returnsFrontendReadyGroundedResponse() throws Exception {
        when(ragClient.ask(any())).thenReturn(new RagAskResponse(
                "Grounded answer",
                0.94,
                false,
                List.of(new RagCitation("Patents Act, 1970", "IND-PAT-ACT-1970", 12, "Section 3",
                        "Parliament of India", "https://example.invalid", "chunk-1")),
                List.of(new RagSource("IND-PAT-ACT-1970", 0.95))
        ));

        mockMvc.perform(post("/api/v1/questions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "Can a classical Ayurvedic formulation be patented?",
                                  "jurisdiction": "INDIA",
                                  "language": "en"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("Grounded answer"))
                .andExpect(jsonPath("$.answerType").value("rag_grounded"))
                .andExpect(jsonPath("$.confidence").value(0.94))
                .andExpect(jsonPath("$.abstained").value(false))
                .andExpect(jsonPath("$.jurisdiction").value("INDIA"))
                .andExpect(jsonPath("$.language").value("en"))
                .andExpect(jsonPath("$.intent").value("PATENT"))
                .andExpect(jsonPath("$.citations[0].documentId").value("IND-PAT-ACT-1970"))
                .andExpect(jsonPath("$.citations[0].page").value(12))
                .andExpect(jsonPath("$.sources[0].documentId").value("IND-PAT-ACT-1970"))
                .andExpect(jsonPath("$.sources[0].score").value(0.95));
    }

    @Test
    void returnsFrontendReadyAbstention() throws Exception {
        when(ragClient.ask(any())).thenReturn(new RagAskResponse(
                "I could not find sufficient authoritative evidence.",
                0.18,
                true,
                List.of(),
                List.of()
        ));

        mockMvc.perform(post("/api/v1/questions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"What is the capital of Mars?\",\"jurisdiction\":\"AUTO\",\"language\":\"en\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answerType").value("abstained"))
                .andExpect(jsonPath("$.confidence").value(0.18))
                .andExpect(jsonPath("$.abstained").value(true))
                .andExpect(jsonPath("$.citations").isEmpty())
                .andExpect(jsonPath("$.sources").isEmpty());
    }

    @Test
    void returnsFrontendReadyGeneralFallback() throws Exception {
        when(ragClient.ask(any())).thenReturn(new RagAskResponse("General answer", 0.4, false, List.of(), List.of()));

        mockMvc.perform(post("/api/v1/questions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"What is machine learning?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answerType").value("general_fallback"))
                .andExpect(jsonPath("$.jurisdiction").value("AUTO"))
                .andExpect(jsonPath("$.language").value("en"))
                .andExpect(jsonPath("$.intent").value("GENERAL"));
    }

    @Test
    void rejectsBlankQuestion() throws Exception {
        mockMvc.perform(post("/api/v1/questions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void rejectsMissingQuestion() throws Exception {
        mockMvc.perform(post("/api/v1/questions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void rejectsVeryLongQuestion() throws Exception {
        String longQuestion = "x".repeat(4001);

        mockMvc.perform(post("/api/v1/questions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"" + longQuestion + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void rejectsInvalidJurisdiction() throws Exception {
        mockMvc.perform(post("/api/v1/questions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"What is a patent?\",\"jurisdiction\":\"MARS\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void rejectsInvalidLanguage() throws Exception {
        mockMvc.perform(post("/api/v1/questions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"What is a patent?\",\"language\":\"fr\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void mapsRagTimeoutToSafeErrorWithoutServiceUrl() throws Exception {
        when(ragClient.ask(any())).thenThrow(RagClientException.timeout());

        mockMvc.perform(post("/api/v1/questions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"What is Section 3(p)?\"}"))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.code").value("RAG_TIMEOUT"))
                .andExpect(jsonPath("$.detail").value("The RAG service timed out."))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("localhost"))));
    }

    @Test
    void mapsRagUnavailableToSafeError() throws Exception {
        when(ragClient.ask(any())).thenThrow(RagClientException.unavailable());

        mockMvc.perform(post("/api/v1/questions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"What is a trademark?\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("RAG_UNAVAILABLE"));
    }

    @Test
    void mapsMalformedRagResponseToSafeError() throws Exception {
        when(ragClient.ask(any())).thenThrow(RagClientException.malformedResponse());

        mockMvc.perform(post("/api/v1/questions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"What is a trademark?\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("RAG_MALFORMED_RESPONSE"));
    }

    @Test
    void mapsTranslationTimeoutToSafeError() throws Exception {
        when(translationProvider.translate(any(), eq(com.ipsakti.ip_sakti_backend.question.model.Language.TA), eq(com.ipsakti.ip_sakti_backend.question.model.Language.EN)))
                .thenThrow(TranslationException.timeout());

        mockMvc.perform(post("/api/v1/questions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "இந்தியாவில் வர்த்தக முத்திரையை பதிவு செய்ய என்ன தேவைகள்?",
                                  "jurisdiction": "INDIA",
                                  "language": "ta"
                                }
                                """))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.code").value("TRANSLATION_TIMEOUT"))
                .andExpect(jsonPath("$.detail").value("The translation service timed out."));
    }

    @Test
    void healthEndpointIsSafe() throws Exception {
        mockMvc.perform(get("/api/v1/questions/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }
}
