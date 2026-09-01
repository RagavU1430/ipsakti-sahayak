package com.ipsakti.ip_sakti_backend.api;

import com.ipsakti.ip_sakti_backend.auth.JwtAuthenticationFilter;
import com.ipsakti.ip_sakti_backend.auth.JwtService;
import com.ipsakti.ip_sakti_backend.auth.UserPrincipal;
import com.ipsakti.ip_sakti_backend.auth.UserService;
import com.ipsakti.ip_sakti_backend.config.SecurityConfig;
import com.ipsakti.ip_sakti_backend.config.SecurityProperties;
import com.ipsakti.ip_sakti_backend.conversation.ConversationService;
import com.ipsakti.ip_sakti_backend.conversation.dto.ConversationPageResponse;
import com.ipsakti.ip_sakti_backend.conversation.entity.UserEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ConversationController.class)
@Import({SecurityConfig.class, com.ipsakti.ip_sakti_backend.config.JacksonConfig.class})
@TestPropertySource(properties = {
        "app.security.mode=prod",
        "app.security.api-key=secret-api-key"
})
class ConversationSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConversationService conversationService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserService userService;

    private UUID userId;
    private UserEntity userEntity;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        userEntity = new UserEntity("prod-user-1", "produser@example.com", "Prod User");
        userEntity.setId(userId);

        when(userService.getOrCreateUser(eq("prod-user-1"), any(), any())).thenReturn(userEntity);
        when(userService.findById(userId)).thenReturn(Optional.of(userEntity));
    }

    @Test
    @DisplayName("In prod mode, request without API key is rejected with 401")
    void testRejectsWithoutApiKeyInProdMode() throws Exception {
        mockMvc.perform(get("/api/v1/conversations"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("In prod mode, request with API key but without user is rejected by controller with 403")
    void testRejectsApiKeyOnlyWithoutUserInProdMode() throws Exception {
        mockMvc.perform(get("/api/v1/conversations")
                        .header("X-API-Key", "secret-api-key"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("In prod mode, request with valid Bearer JWT and API key succeeds")
    void testAcceptsValidJwtWithApiKeyInProdMode() throws Exception {
        when(jwtService.parseAndValidateToken("valid.jwt.token"))
                .thenReturn(Optional.of(new JwtService.JwtClaims("prod-user-1", "produser@example.com", "authenticated", java.time.Instant.now(), java.time.Instant.now().plusSeconds(600), java.util.Map.of())));
        when(conversationService.listConversations(any(UserPrincipal.class), eq(0), eq(20)))
                .thenReturn(new ConversationPageResponse(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/api/v1/conversations")
                        .header("X-API-Key", "secret-api-key")
                        .header("Authorization", "Bearer valid.jwt.token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());
    }
}
