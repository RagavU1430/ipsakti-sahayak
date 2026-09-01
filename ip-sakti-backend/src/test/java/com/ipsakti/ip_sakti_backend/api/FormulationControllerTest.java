package com.ipsakti.ip_sakti_backend.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ipsakti.ip_sakti_backend.config.SecurityConfig;
import com.ipsakti.ip_sakti_backend.exception.RagClientException;
import com.ipsakti.ip_sakti_backend.formulation.FormulationClassificationService;
import com.ipsakti.ip_sakti_backend.formulation.classification.FormulationClarificationService;
import com.ipsakti.ip_sakti_backend.formulation.classification.FormulationRuleEngine;
import com.ipsakti.ip_sakti_backend.formulation.classification.RegulatoryRouteService;
import com.ipsakti.ip_sakti_backend.multilingual.BhashiniClient;
import com.ipsakti.ip_sakti_backend.multilingual.TranslationService;
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

@WebMvcTest(FormulationController.class)
@Import({
        SecurityConfig.class,
        FormulationClassificationService.class,
        FormulationRuleEngine.class,
        FormulationClarificationService.class,
        RegulatoryRouteService.class,
        TranslationService.class
})
class FormulationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RagClient ragClient;

    @MockitoBean
    private BhashiniClient bhashiniClient;

    @Test
    void classifiesValidFormulationRequest() throws Exception {
        when(ragClient.ask(any())).thenReturn(groundedRagResponse());

        mockMvc.perform(post("/api/v1/formulations/classify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productName": "Digestive Health Mix",
                                  "ingredients": ["Cumin", "Fennel"],
                                  "dosageForm": "powder",
                                  "intendedUse": "daily food-like digestive support",
                                  "claims": ["supports digestion", "nutrition support"],
                                  "traditionalUse": false,
                                  "targetMarket": "India"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.classification").value("AYURVEDA_AAHAR_NUTRACEUTICAL"))
                .andExpect(jsonPath("$.status").value("classified"))
                .andExpect(jsonPath("$.needsClarification").value(false))
                .andExpect(jsonPath("$.regulatoryRoute.route").value("AYURVEDA_AAHAR"))
                .andExpect(jsonPath("$.citations[0].documentId").value("IND-AYUSH-TEST"))
                .andExpect(jsonPath("$.sources[0].score").value(0.91));
    }

    @Test
    void rejectsMalformedRequest() throws Exception {
        mockMvc.perform(post("/api/v1/formulations/classify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void rejectsBlankProductName() throws Exception {
        mockMvc.perform(post("/api/v1/formulations/classify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productName\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void rejectsExcessiveIngredientArray() throws Exception {
        String ingredients = "\"" + String.join("\",\"", java.util.Collections.nCopies(26, "Ingredient")) + "\"";

        mockMvc.perform(post("/api/v1/formulations/classify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productName\":\"Test Product\",\"ingredients\":[" + ingredients + "]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void mapsRagUnavailableToControlledError() throws Exception {
        when(ragClient.ask(any())).thenThrow(RagClientException.unavailable());

        mockMvc.perform(post("/api/v1/formulations/classify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productName\":\"Herbal Cream\",\"intendedUse\":\"skin cosmetic\",\"claims\":[\"glow\"]}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("RAG_UNAVAILABLE"));
    }

    private RagAskResponse groundedRagResponse() {
        return new RagAskResponse(
                "RAG regulatory context",
                0.91,
                false,
                List.of(new RagCitation(
                        "Ayush Evidence",
                        "IND-AYUSH-TEST",
                        10,
                        "Regulatory context",
                        "Test Authority",
                        "https://example.invalid",
                        "chunk-1"
                )),
                List.of(new RagSource("IND-AYUSH-TEST", 0.91))
        );
    }
}
