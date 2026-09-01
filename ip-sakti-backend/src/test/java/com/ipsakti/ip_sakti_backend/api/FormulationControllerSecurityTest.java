package com.ipsakti.ip_sakti_backend.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ipsakti.ip_sakti_backend.config.SecurityConfig;
import com.ipsakti.ip_sakti_backend.formulation.FormulationClassificationService;
import com.ipsakti.ip_sakti_backend.formulation.classification.FormulationClarificationService;
import com.ipsakti.ip_sakti_backend.formulation.classification.FormulationRuleEngine;
import com.ipsakti.ip_sakti_backend.formulation.classification.RegulatoryRouteService;
import com.ipsakti.ip_sakti_backend.multilingual.BhashiniClient;
import com.ipsakti.ip_sakti_backend.multilingual.TranslationService;
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

@WebMvcTest(FormulationController.class)
@Import({
        SecurityConfig.class,
        FormulationClassificationService.class,
        FormulationRuleEngine.class,
        FormulationClarificationService.class,
        RegulatoryRouteService.class,
        TranslationService.class
})
@TestPropertySource(properties = {
        "app.security.mode=prod",
        "app.security.api-key=test-api-key"
})
class FormulationControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RagClient ragClient;

    @MockitoBean
    private BhashiniClient bhashiniClient;

    @Test
    void rejectsWithoutApiKeyInProdMode() throws Exception {
        mockMvc.perform(post("/api/v1/formulations/classify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productName\":\"Herbal Cream\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void acceptsWithApiKeyInProdMode() throws Exception {
        when(ragClient.ask(any())).thenReturn(new RagAskResponse("Evidence", 0.9, false, List.of(), List.of()));

        mockMvc.perform(post("/api/v1/formulations/classify")
                        .header("X-API-Key", "test-api-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productName\":\"Herbal Cream\",\"intendedUse\":\"skin cosmetic\",\"claims\":[\"glow\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("classified"));
    }
}
