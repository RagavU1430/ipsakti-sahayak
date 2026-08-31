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
import com.ipsakti.ip_sakti_backend.rag.dto.RagCitation;
import com.ipsakti.ip_sakti_backend.rag.dto.RagSource;
import com.ipsakti.ip_sakti_backend.regulatory.RegulatoryAnalysisService;
import com.ipsakti.ip_sakti_backend.regulatory.engine.AbsAnalysisService;
import com.ipsakti.ip_sakti_backend.regulatory.engine.GratkAnalysisService;
import com.ipsakti.ip_sakti_backend.regulatory.engine.RegulatoryEvidenceMapper;
import com.ipsakti.ip_sakti_backend.regulatory.engine.RegulatoryJurisdictionRouter;
import com.ipsakti.ip_sakti_backend.regulatory.engine.Section3eAnalysisService;
import com.ipsakti.ip_sakti_backend.regulatory.engine.Section3pAnalysisService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RegulatoryController.class)
@Import({
        SecurityConfig.class,
        RegulatoryAnalysisService.class,
        RegulatoryJurisdictionRouter.class,
        RegulatoryEvidenceMapper.class,
        Section3pAnalysisService.class,
        Section3eAnalysisService.class,
        AbsAnalysisService.class,
        GratkAnalysisService.class
})
class RegulatoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RagClient ragClient;

    @Test
    void returnsStructuredRegulatoryAnalysis() throws Exception {
        when(ragClient.ask(any())).thenReturn(grounded());

        mockMvc.perform(post("/api/v1/regulatory/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productName": "Ayurvedic Formulation",
                                  "ingredients": ["Plant A", "Plant B"],
                                  "intendedUse": "traditional knowledge based therapeutic use",
                                  "claims": ["known ingredients combination"],
                                  "traditionalKnowledge": true,
                                  "biologicalResources": true,
                                  "geneticResources": true,
                                  "resourceOrigin": "India",
                                  "targetMarket": "India",
                                  "jurisdiction": "INDIA",
                                  "knownIngredients": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jurisdiction").value("INDIA"))
                .andExpect(jsonPath("$.overallStatus").value("REVIEW_RECOMMENDED"))
                .andExpect(jsonPath("$.engines.length()").value(4))
                .andExpect(jsonPath("$.engines[0].citations[0].documentId").value("DOC-1"));
    }

    @Test
    void malformedRequestReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/regulatory/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void excessivePayloadReturnsBadRequest() throws Exception {
        String ingredients = "\"" + String.join("\",\"", java.util.Collections.nCopies(26, "Ingredient")) + "\"";

        mockMvc.perform(post("/api/v1/regulatory/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productName\":\"Test\",\"ingredients\":[" + ingredients + "],\"jurisdiction\":\"INDIA\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void invalidJurisdictionReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/regulatory/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productName\":\"Test\",\"jurisdiction\":\"MARS\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void ragUnavailableReturnsControlledError() throws Exception {
        when(ragClient.ask(any())).thenThrow(RagClientException.unavailable());

        mockMvc.perform(post("/api/v1/regulatory/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productName\":\"Test\",\"targetMarket\":\"India\",\"jurisdiction\":\"INDIA\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("RAG_UNAVAILABLE"));
    }

    private RagAskResponse grounded() {
        return new RagAskResponse(
                "Grounded legal evidence",
                0.86,
                false,
                List.of(new RagCitation("Evidence Act", "DOC-1", 1, "Section", "Authority", "https://example.invalid", "chunk-1")),
                List.of(new RagSource("DOC-1", 0.88))
        );
    }
}
