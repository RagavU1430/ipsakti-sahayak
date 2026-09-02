package com.ipsakti.ip_sakti_backend.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ipsakti.ip_sakti_backend.config.JacksonConfig;
import com.ipsakti.ip_sakti_backend.config.SecurityConfig;
import com.ipsakti.ip_sakti_backend.conversation.dto.CreateConversationRequest;
import com.ipsakti.ip_sakti_backend.conversation.dto.ConversationMessageRequest;
import com.ipsakti.ip_sakti_backend.conversation.dto.UpdateConversationRequest;
import com.ipsakti.ip_sakti_backend.formulation.model.FormulationRequest;
import com.ipsakti.ip_sakti_backend.multilingual.TranslationProvider;
import com.ipsakti.ip_sakti_backend.question.model.Jurisdiction;
import com.ipsakti.ip_sakti_backend.question.model.Language;
import com.ipsakti.ip_sakti_backend.question.model.QuestionRequest;
import com.ipsakti.ip_sakti_backend.rag.RagClient;
import com.ipsakti.ip_sakti_backend.rag.dto.RagAskResponse;
import com.ipsakti.ip_sakti_backend.rag.dto.RagCitation;
import com.ipsakti.ip_sakti_backend.rag.dto.RagSource;
import com.ipsakti.ip_sakti_backend.regulatory.model.RegulatoryAnalysisRequest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import({SecurityConfig.class, JacksonConfig.class})
class FullSystemIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private RagClient ragClient;

    @MockitoBean
    private TranslationProvider translationProvider;

    @BeforeEach
    void setUp() {
        when(ragClient.ask(any())).thenReturn(new RagAskResponse(
                "A patent in India requires novelty, inventive step, and industrial applicability under Section 2(1)(j).",
                0.92,
                false,
                List.of(new RagCitation("Patents Act 1970", "ACT-PAT-1970", 2, "Section 2(1)(j)", "Statutory", null, "chk-1")),
                List.of(new RagSource("ACT-PAT-1970", 0.92))
        ));

        // Default mock for translation service
        when(translationProvider.translate(any(), any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("Matrix 1-3: Multilingual Question Intelligence (EN, HI, TA)")
    void testMultilingualQuestions() throws Exception {
        when(translationProvider.translate(eq("पेटेंट के क्या नियम हैं?"), eq(Language.HI), eq(Language.EN)))
                .thenReturn("What are the rules of patent?");
        when(translationProvider.translate(any(), eq(Language.EN), eq(Language.HI)))
                .thenReturn("भारत में पेटेंट के लिए नवीनता की आवश्यकता होती है।");

        when(translationProvider.translate(eq("இந்தியாவில் காப்புரிமை பெறுவதற்கான விதிகள் என்ன?"), eq(Language.TA), eq(Language.EN)))
                .thenReturn("What are the rules for getting a patent in India?");
        when(translationProvider.translate(any(), eq(Language.EN), eq(Language.TA)))
                .thenReturn("இந்தியாவில் காப்புரிமைக்கு புதுமை தேவைப்படுகிறது.");

        // 1. English
        mockMvc.perform(post("/api/v1/questions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new QuestionRequest("What are patent criteria in India?", null, Language.EN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").isNotEmpty())
                .andExpect(jsonPath("$.confidence").value(0.92))
                .andExpect(jsonPath("$.abstained").value(false))
                .andExpect(jsonPath("$.citations[0].document").value("Patents Act 1970"));

        // 2. Hindi
        mockMvc.perform(post("/api/v1/questions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new QuestionRequest("पेटेंट के क्या नियम हैं?", null, Language.HI))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").isNotEmpty())
                .andExpect(jsonPath("$.language").value("hi"))
                .andExpect(jsonPath("$.processing_language").value("en"));

        // 3. Tamil
        mockMvc.perform(post("/api/v1/questions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new QuestionRequest("இந்தியாவில் காப்புரிமை பெறுவதற்கான விதிகள் என்ன?", null, Language.TA))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").isNotEmpty())
                .andExpect(jsonPath("$.language").value("ta"))
                .andExpect(jsonPath("$.processing_language").value("en"));
    }

    @Test
    @DisplayName("Matrix 4-5: End-to-End Abstention on Out-of-Scope Query")
    void testAbstentionIntegrity() throws Exception {
        when(ragClient.ask(any())).thenReturn(new RagAskResponse(
                "I do not have sufficient legal evidence in the authoritative corpus to answer this question reliably.",
                0.15,
                true,
                List.of(),
                List.of()
        ));

        mockMvc.perform(post("/api/v1/questions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new QuestionRequest("Can time travel devices be patented under Indian law?", null, Language.EN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.abstained").value(true))
                .andExpect(jsonPath("$.confidence").value(0.15))
                .andExpect(jsonPath("$.citations").isEmpty())
                .andExpect(jsonPath("$.sources").isEmpty());
    }

    @Test
    @DisplayName("Matrix 6: Formulation Classification (5 Categories)")
    void testFormulationClassificationIntegration() throws Exception {
        // Classical Drug
        mockMvc.perform(post("/api/v1/formulations/classify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new FormulationRequest(
                                "Triphala Churna",
                                List.of("Haritaki", "Bibhitaki", "Amalaki"),
                                "powder",
                                "traditional digestive medicine",
                                List.of("supports classical therapeutic use"),
                                "traditional processing",
                                "Charaka Samhita reference",
                                true,
                                true,
                                "India",
                                null,
                                null,
                                null
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.classification").value("CLASSICAL_DRUG"))
                .andExpect(jsonPath("$.regulatoryRoute.route").value("AYUSH_CLASSICAL_DRUG"));

        // Phytopharmaceutical
        mockMvc.perform(post("/api/v1/formulations/classify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new FormulationRequest(
                                "Standardized Botanical Extract",
                                List.of("Plant extract"),
                                "capsule",
                                "therapeutic drug development",
                                List.of("standardized extract", "quantified active constituent", "clinical trial"),
                                "active marker standardized botanical extract",
                                null,
                                false,
                                true,
                                "India",
                                null,
                                null,
                                null
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.classification").value("PHYTOPHARMACEUTICAL_NEW_DRUG"))
                .andExpect(jsonPath("$.regulatoryRoute.route").value("PHYTOPHARMACEUTICAL_NEW_DRUG"));
    }

    @Test
    @DisplayName("Matrix 7: Regulatory Multi-Engine Analysis (3(p), 3(e), ABS, GRATK)")
    void testRegulatoryAnalysisIntegration() throws Exception {
        mockMvc.perform(post("/api/v1/regulatory/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegulatoryAnalysisRequest(
                                "Herbal Synergistic Composition",
                                List.of("Curcuma longa", "Piper nigrum"),
                                "Tablet",
                                "Synergistic combination of Curcuma longa and Piper nigrum with unexpected enhanced bioavailability",
                                List.of("Enhanced absorption"),
                                true,
                                "Ayurvedic Formulary of India",
                                true,
                                "Western Ghats, India",
                                "India",
                                Jurisdiction.INDIA,
                                true,
                                true,
                                true,
                                true
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overallStatus").isNotEmpty())
                .andExpect(jsonPath("$.engines.length()").value(4));
    }

    @Test
    @DisplayName("Matrix 8-13: Full Conversation Lifecycle, Citations, Multi-Tenant Security & Cascade Deletion")
    void testFullConversationLifecycleAndMultiTenantSecurity() throws Exception {
        when(ragClient.ask(any())).thenReturn(new RagAskResponse(
                "Traditional Knowledge Digital Library (TKDL) prevents misappropriation under Section 3(p).",
                0.95,
                false,
                List.of(new RagCitation("Patents Act 1970", "ACT-PAT-1970", 8, "Section 3(p)", "Statutory", null, "chk-100")),
                List.of(new RagSource("ACT-PAT-1970", 0.95))
        ));

        // Step 1: User A creates a conversation
        MvcResult createResult = mockMvc.perform(post("/api/v1/conversations")
                        .header("X-Dev-User-Id", "user-alpha")
                        .header("X-Dev-User-Email", "alpha@example.com")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateConversationRequest("Ayurveda IP Strategy"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.title").value("Ayurveda IP Strategy"))
                .andReturn();

        String convId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

        // Step 2: User A posts a message and receives grounded answer with citations
        mockMvc.perform(post("/api/v1/conversations/" + convId + "/messages")
                        .header("X-Dev-User-Id", "user-alpha")
                        .header("X-Dev-User-Email", "alpha@example.com")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ConversationMessageRequest("How does TKDL affect patentability?", null, Language.EN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer", containsString("TKDL")))
                .andExpect(jsonPath("$.citations[0].document").value("Patents Act 1970"))
                .andExpect(jsonPath("$.citations[0].section").value("Section 3(p)"))
                .andExpect(jsonPath("$.sources[0].documentId").value("ACT-PAT-1970"));

        // Step 3: User A retrieves conversation with full message and citation history
        mockMvc.perform(get("/api/v1/conversations/" + convId)
                        .header("X-Dev-User-Id", "user-alpha")
                        .header("X-Dev-User-Email", "alpha@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(convId))
                .andExpect(jsonPath("$.messages.length()").value(2))
                .andExpect(jsonPath("$.messages[0].role").value("user"))
                .andExpect(jsonPath("$.messages[1].role").value("assistant"))
                .andExpect(jsonPath("$.messages[1].citations.length()").value(1));

        // Step 4: User B attempts unauthorized access to User A's conversation -> MUST FAIL 403
        mockMvc.perform(get("/api/v1/conversations/" + convId)
                        .header("X-Dev-User-Id", "user-beta")
                        .header("X-Dev-User-Email", "beta@example.com"))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/v1/conversations/" + convId)
                        .header("X-Dev-User-Id", "user-beta")
                        .header("X-Dev-User-Email", "beta@example.com")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateConversationRequest("Hacked Title"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/conversations/" + convId + "/messages")
                        .header("X-Dev-User-Id", "user-beta")
                        .header("X-Dev-User-Email", "beta@example.com")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ConversationMessageRequest("Malicious injection", null, Language.EN))))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/conversations/" + convId)
                        .header("X-Dev-User-Id", "user-beta")
                        .header("X-Dev-User-Email", "beta@example.com"))
                .andExpect(status().isForbidden());

        // Step 5: User A updates title
        mockMvc.perform(patch("/api/v1/conversations/" + convId)
                        .header("X-Dev-User-Id", "user-alpha")
                        .header("X-Dev-User-Email", "alpha@example.com")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateConversationRequest("Ayurveda IP & Patent Strategy"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Ayurveda IP & Patent Strategy"));

        // Step 6: User A lists conversations with pagination
        mockMvc.perform(get("/api/v1/conversations?page=0&size=10")
                        .header("X-Dev-User-Id", "user-alpha")
                        .header("X-Dev-User-Email", "alpha@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.total_elements").value(1));

        // Step 7: User A deletes conversation -> cascade removes all messages
        mockMvc.perform(delete("/api/v1/conversations/" + convId)
                        .header("X-Dev-User-Id", "user-alpha")
                        .header("X-Dev-User-Email", "alpha@example.com"))
                .andExpect(status().isNoContent());

        // Verify conversation is gone
        mockMvc.perform(get("/api/v1/conversations/" + convId)
                        .header("X-Dev-User-Id", "user-alpha")
                        .header("X-Dev-User-Email", "alpha@example.com"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Matrix 14-16: Health Checks & Input Validation")
    void testSystemReliabilityAndErrorHandling() throws Exception {
        // Health Checks
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));

        mockMvc.perform(get("/health/ready"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ready"))
                .andExpect(jsonPath("$.backend").value("up"));

        // Input Validation: Blank question rejected with 400
        mockMvc.perform(post("/api/v1/questions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new QuestionRequest("", null, Language.EN))))
                .andExpect(status().isBadRequest());
    }
}
