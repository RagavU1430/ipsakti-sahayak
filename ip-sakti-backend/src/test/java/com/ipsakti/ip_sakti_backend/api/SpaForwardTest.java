package com.ipsakti.ip_sakti_backend.api;

import com.ipsakti.ip_sakti_backend.config.JacksonConfig;
import com.ipsakti.ip_sakti_backend.config.SecurityConfig;
import com.ipsakti.ip_sakti_backend.config.SpaForwardController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SpaForwardController.class)
@Import({SecurityConfig.class, JacksonConfig.class})
class SpaForwardTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Root / forwards to index.html")
    void testRootForwardsToIndexHtml() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
    }

    @Test
    @DisplayName("Frontend SPA route /ask forwards to index.html")
    void testAskForwardsToIndexHtml() throws Exception {
        mockMvc.perform(get("/ask"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
    }

    @Test
    @DisplayName("Frontend SPA route /formulations forwards to index.html")
    void testFormulationsForwardsToIndexHtml() throws Exception {
        mockMvc.perform(get("/formulations"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
    }

    @Test
    @DisplayName("Frontend SPA route /regulatory forwards to index.html")
    void testRegulatoryForwardsToIndexHtml() throws Exception {
        mockMvc.perform(get("/regulatory"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
    }

    @Test
    @DisplayName("Frontend SPA route /about forwards to index.html")
    void testAboutForwardsToIndexHtml() throws Exception {
        mockMvc.perform(get("/about"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
    }
}
