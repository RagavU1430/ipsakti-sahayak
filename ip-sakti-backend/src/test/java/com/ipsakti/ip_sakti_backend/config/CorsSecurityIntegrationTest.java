package com.ipsakti.ip_sakti_backend.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "rag.base-url=http://localhost:8000",
        "app.security.mode=dev"
})
@AutoConfigureMockMvc
class CorsSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private static final String ALLOWED_ORIGIN_LOCALHOST = "http://localhost:5173";
    private static final String ALLOWED_ORIGIN_127 = "http://127.0.0.1:5173";
    private static final String UNEXPECTED_ORIGIN = "http://malicious-attacker.com";

    @Test
    @DisplayName("CORS Preflight: OPTIONS request from allowed localhost origin succeeds with proper headers")
    void testPreflightAllowedOriginLocalhost() throws Exception {
        mockMvc.perform(options("/api/v1/ask")
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN_LOCALHOST)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.POST.name())
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type, Authorization, X-Dev-User-Id"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN_LOCALHOST))
                .andExpect(header().exists(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS))
                .andExpect(header().exists(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_MAX_AGE, "3600"));
    }

    @Test
    @DisplayName("CORS Preflight: OPTIONS request from allowed 127.0.0.1 origin succeeds")
    void testPreflightAllowedOrigin127() throws Exception {
        mockMvc.perform(options("/api/v1/questions")
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN_127)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.POST.name())
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type, Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN_127))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_MAX_AGE, "3600"));
    }

    @Test
    @DisplayName("CORS Preflight: OPTIONS request from unexpected origin is rejected (no allow-origin header)")
    void testPreflightUnexpectedOriginRejected() throws Exception {
        mockMvc.perform(options("/api/v1/ask")
                        .header(HttpHeaders.ORIGIN, UNEXPECTED_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.POST.name()))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    @DisplayName("CORS Simple GET: GET /health from allowed origin returns allow-origin header")
    void testSimpleGetAllowedOrigin() throws Exception {
        mockMvc.perform(get("/health")
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN_LOCALHOST))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN_LOCALHOST));
    }

    @Test
    @DisplayName("CORS Simple GET: GET /health from unexpected origin does not return allow-origin header")
    void testSimpleGetUnexpectedOrigin() throws Exception {
        mockMvc.perform(get("/health")
                        .header(HttpHeaders.ORIGIN, UNEXPECTED_ORIGIN))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    @DisplayName("CORS Preflight on Formulations endpoint: succeeds with allowed headers")
    void testPreflightFormulations() throws Exception {
        mockMvc.perform(options("/api/v1/formulations/classify")
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN_LOCALHOST)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.POST.name())
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN_LOCALHOST));
    }

    @Test
    @DisplayName("CORS Preflight on Regulatory endpoint: succeeds with allowed headers")
    void testPreflightRegulatory() throws Exception {
        mockMvc.perform(options("/api/v1/regulatory/analyze")
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN_LOCALHOST)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.POST.name())
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN_LOCALHOST));
    }

    @Test
    @DisplayName("CORS Preflight on TK Overlap endpoint: succeeds with allowed headers")
    void testPreflightTkOverlap() throws Exception {
        mockMvc.perform(options("/api/v1/tk/overlap")
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN_LOCALHOST)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.POST.name())
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN_LOCALHOST));
    }

    @Test
    @DisplayName("CORS Preflight on Conversations endpoint: succeeds with custom headers")
    void testPreflightConversations() throws Exception {
        mockMvc.perform(options("/api/v1/conversations")
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN_LOCALHOST)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.POST.name())
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type, Authorization, X-Dev-User-Id"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN_LOCALHOST));
    }
}
