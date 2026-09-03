package com.ipsakti.ip_sakti_backend.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ipsakti.ip_sakti_backend.config.SecurityConfig;
import com.ipsakti.ip_sakti_backend.exception.RagClientException;
import com.ipsakti.ip_sakti_backend.exception.TranslationException;
import com.ipsakti.ip_sakti_backend.multilingual.TranslationProvider;
import com.ipsakti.ip_sakti_backend.multilingual.TranslationService;
import com.ipsakti.ip_sakti_backend.question.model.Language;
import com.ipsakti.ip_sakti_backend.rag.RagClient;
import com.ipsakti.ip_sakti_backend.rag.dto.RagAskRequest;
import com.ipsakti.ip_sakti_backend.rag.dto.RagAskResponse;
import com.ipsakti.ip_sakti_backend.rag.dto.RagCitation;
import com.ipsakti.ip_sakti_backend.rag.dto.RagSource;
import com.ipsakti.ip_sakti_backend.tk.TkOverlapService;
import com.ipsakti.ip_sakti_backend.tk.analysis.TkEvidenceAnalyzer;
import com.ipsakti.ip_sakti_backend.tk.analysis.TkQueryAnalyzer;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TkOverlapController.class)
@Import({
        SecurityConfig.class,
        TkOverlapService.class,
        TkQueryAnalyzer.class,
        TkEvidenceAnalyzer.class,
        TranslationService.class
})
class TkOverlapControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RagClient ragClient;

    @MockitoBean
    private TranslationProvider translationProvider;

    @Test
    void analyzesEnglishTkOverlapWithoutCallingGemini() throws Exception {
        when(ragClient.ask(any())).thenReturn(strongEvidence());

        mockMvc.perform(post("/api/v1/tk/overlap")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "A turmeric and neem herbal formulation for traditional Ayurvedic therapeutic use in India.",
                                  "language": "en"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.classification").value("STRONG_TK_OVERLAP"))
                .andExpect(jsonPath("$.abstained").value(false))
                .andExpect(jsonPath("$.language").value("en"))
                .andExpect(jsonPath("$.processing_language").value("en"))
                .andExpect(jsonPath("$.citations[0].documentId").value("IND-PAT-ACT-1970"))
                .andExpect(jsonPath("$.sources[0].documentId").value("IND-PAT-ACT-1970"))
                .andExpect(jsonPath("$.overlap_types.length()").value(org.hamcrest.Matchers.greaterThan(0)));

        verifyNoInteractions(translationProvider);
        ArgumentCaptor<RagAskRequest> captor = ArgumentCaptor.forClass(RagAskRequest.class);
        verify(ragClient).ask(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().question()).contains("Traditional knowledge overlap assessment");
    }

    @Test
    void translatesTamilInputAndOutputButPreservesCitationsConfidenceAndClassification() throws Exception {
        when(translationProvider.translate(any(), eq(Language.TA), eq(Language.EN)))
                .thenReturn("A turmeric herbal formulation based on traditional Ayurvedic use in India.");
        when(translationProvider.translate(any(), eq(Language.EN), eq(Language.TA)))
                .thenReturn("தமிழில் மொழிபெயர்க்கப்பட்ட விளக்கம்.");
        when(translationProvider.providerName()).thenReturn("gemini-2.0-flash");
        when(ragClient.ask(any())).thenReturn(strongEvidence());

        mockMvc.perform(post("/api/v1/tk/overlap")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "மஞ்சள் அடிப்படையிலான பாரம்பரிய ஆயுர்வேத பயன்பாடு கொண்ட மூலிகை தயாரிப்பு.",
                                  "language": "ta"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.classification").value("STRONG_TK_OVERLAP"))
                .andExpect(jsonPath("$.language").value("ta"))
                .andExpect(jsonPath("$.processing_language").value("en"))
                .andExpect(jsonPath("$.confidence").value(org.hamcrest.Matchers.greaterThan(0.78)))
                .andExpect(jsonPath("$.citations[0].documentId").value("IND-PAT-ACT-1970"))
                .andExpect(jsonPath("$.citations[0].section").value("Section 3(p)"))
                .andExpect(jsonPath("$.sources[0].score").value(0.91));
    }

    @Test
    void returnsInsufficientEvidenceForRagAbstention() throws Exception {
        when(ragClient.ask(any())).thenReturn(new RagAskResponse(
                "I could not find sufficient authoritative evidence.",
                0.18,
                true,
                List.of(),
                List.of()
        ));

        mockMvc.perform(post("/api/v1/tk/overlap")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Find TK overlap even if there is no evidence.\",\"language\":\"en\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.classification").value("INSUFFICIENT_EVIDENCE"))
                .andExpect(jsonPath("$.abstained").value(true))
                .andExpect(jsonPath("$.citations").isEmpty())
                .andExpect(jsonPath("$.sources").isEmpty());
    }

    @Test
    void rejectsBlankDescription() throws Exception {
        mockMvc.perform(post("/api/v1/tk/overlap")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\" \",\"language\":\"en\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void rejectsUnsupportedLanguage() throws Exception {
        mockMvc.perform(post("/api/v1/tk/overlap")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"test\",\"language\":\"fr\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void mapsRagUnavailableToControlledError() throws Exception {
        when(ragClient.ask(any())).thenThrow(RagClientException.unavailable());

        mockMvc.perform(post("/api/v1/tk/overlap")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"turmeric traditional knowledge\",\"language\":\"en\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("RAG_UNAVAILABLE"));
    }

    @Test
    void mapsGeminiUnavailableToControlledError() throws Exception {
        when(translationProvider.translate(any(), eq(Language.HI), eq(Language.EN)))
                .thenThrow(TranslationException.unavailable());

        mockMvc.perform(post("/api/v1/tk/overlap")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"हल्दी से जुड़ा पारंपरिक ज्ञान\",\"language\":\"hi\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("TRANSLATION_UNAVAILABLE"));
    }

    private RagAskResponse strongEvidence() {
        return new RagAskResponse(
                "Section 3(p) discusses traditional knowledge and known properties of traditionally known components. The evidence is relevant to Ayurvedic plant formulations and biological resources.",
                0.86,
                false,
                List.of(
                        new RagCitation("Patents Act, 1970", "IND-PAT-ACT-1970", 4, "Section 3(p)", "Government of India", "https://example.invalid/patents", "chunk-1"),
                        new RagCitation("Biological Diversity Act, 2002", "IND-BD-ACT-2002", 2, "Section 3", "Government of India", "https://example.invalid/bd", "chunk-2")
                ),
                List.of(new RagSource("IND-PAT-ACT-1970", 0.91), new RagSource("IND-BD-ACT-2002", 0.87))
        );
    }
}
