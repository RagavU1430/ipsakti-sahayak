package com.ipsakti.ip_sakti_backend.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ipsakti.ip_sakti_backend.config.SecurityConfig;
import com.ipsakti.ip_sakti_backend.rag.RagClient;
import com.ipsakti.ip_sakti_backend.rag.dto.RagAskResponse;
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
import org.springframework.test.context.TestPropertySource;
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
@TestPropertySource(properties = {
        "app.security.mode=prod",
        "app.security.api-key=test-api-key"
})
class RegulatoryControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RagClient ragClient;

    @Test
    void rejectsWithoutApiKeyInProdMode() throws Exception {
        mockMvc.perform(post("/api/v1/regulatory/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productName\":\"Test\",\"jurisdiction\":\"INDIA\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void acceptsWithApiKeyInProdMode() throws Exception {
        when(ragClient.ask(any())).thenReturn(new RagAskResponse("Evidence", 0.8, false, List.of(), List.of()));

        mockMvc.perform(post("/api/v1/regulatory/analyze")
                        .header("X-API-Key", "test-api-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productName\":\"Test\",\"targetMarket\":\"India\",\"jurisdiction\":\"INDIA\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.engines.length()").value(4));
    }
}
