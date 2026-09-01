package com.ipsakti.ip_sakti_backend.api;

import com.ipsakti.ip_sakti_backend.auth.JwtAuthenticationFilter;
import com.ipsakti.ip_sakti_backend.auth.JwtService;
import com.ipsakti.ip_sakti_backend.auth.UserPrincipal;
import com.ipsakti.ip_sakti_backend.auth.UserService;
import com.ipsakti.ip_sakti_backend.config.SecurityConfig;
import com.ipsakti.ip_sakti_backend.config.SecurityProperties;
import com.ipsakti.ip_sakti_backend.conversation.ConversationService;
import com.ipsakti.ip_sakti_backend.conversation.dto.ConversationDetailResponse;
import com.ipsakti.ip_sakti_backend.conversation.dto.ConversationMessageRequest;
import com.ipsakti.ip_sakti_backend.conversation.dto.ConversationMessageResponse;
import com.ipsakti.ip_sakti_backend.conversation.dto.ConversationPageResponse;
import com.ipsakti.ip_sakti_backend.conversation.dto.ConversationSummaryResponse;
import com.ipsakti.ip_sakti_backend.conversation.dto.CreateConversationRequest;
import com.ipsakti.ip_sakti_backend.conversation.dto.MessageDetailResponse;
import com.ipsakti.ip_sakti_backend.conversation.dto.UpdateConversationRequest;
import com.ipsakti.ip_sakti_backend.conversation.entity.UserEntity;
import com.ipsakti.ip_sakti_backend.exception.ConversationAccessDeniedException;
import com.ipsakti.ip_sakti_backend.exception.ConversationNotFoundException;
import com.ipsakti.ip_sakti_backend.question.model.Jurisdiction;
import com.ipsakti.ip_sakti_backend.question.model.Language;
import com.ipsakti.ip_sakti_backend.question.model.QuestionCitation;
import com.ipsakti.ip_sakti_backend.question.model.QuestionIntent;
import com.ipsakti.ip_sakti_backend.question.model.QuestionSource;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ConversationController.class)
@Import({SecurityConfig.class, com.ipsakti.ip_sakti_backend.config.JacksonConfig.class})
class ConversationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConversationService conversationService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private SecurityProperties securityProperties;

    private UUID userId;
    private UserEntity userEntity;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        userEntity = new UserEntity("test-user-1", "user1@example.com", "Test User");
        userEntity.setId(userId);

        when(securityProperties.isDevMode()).thenReturn(true);
        when(securityProperties.apiKeyRequired()).thenReturn(false);
        when(securityProperties.getAllowedOrigins()).thenReturn(List.of("http://localhost:5173"));
        when(userService.getOrCreateUser(eq("test-user-1"), any(), any())).thenReturn(userEntity);
        when(userService.findById(userId)).thenReturn(Optional.of(userEntity));
    }

    @Test
    @DisplayName("Create conversation returns 201 Created with metadata")
    void testCreateConversation() throws Exception {
        UUID convId = UUID.randomUUID();
        when(conversationService.createConversation(any(UserPrincipal.class), any(CreateConversationRequest.class)))
                .thenReturn(new ConversationSummaryResponse(convId, "Ayurvedic Patent", Instant.now(), Instant.now()));

        mockMvc.perform(post("/api/v1/conversations")
                        .header("X-Dev-User-Id", "test-user-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Ayurvedic Patent\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(convId.toString()))
                .andExpect(jsonPath("$.title").value("Ayurvedic Patent"));
    }

    @Test
    @DisplayName("List conversations returns paginated summaries")
    void testListConversations() throws Exception {
        UUID convId = UUID.randomUUID();
        ConversationSummaryResponse item = new ConversationSummaryResponse(convId, "My Chat", Instant.now(), Instant.now());
        when(conversationService.listConversations(any(UserPrincipal.class), eq(0), eq(20)))
                .thenReturn(new ConversationPageResponse(List.of(item), 0, 20, 1, 1));

        mockMvc.perform(get("/api/v1/conversations?page=0&size=20")
                        .header("X-Dev-User-Id", "test-user-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(convId.toString()))
                .andExpect(jsonPath("$.items[0].title").value("My Chat"))
                .andExpect(jsonPath("$.total_elements").value(1));
    }

    @Test
    @DisplayName("Get conversation returns full details with citations")
    void testGetConversation() throws Exception {
        UUID convId = UUID.randomUUID();
        UUID msgId = UUID.randomUUID();
        MessageDetailResponse msg = new MessageDetailResponse(
                msgId,
                "assistant",
                "Patent answer",
                "RAG_GROUNDED",
                0.95,
                false,
                "INDIA",
                "en",
                "en",
                "en",
                "PRODUCT_QUESTION",
                List.of(new QuestionCitation("Patents Act 1970", "IND-PAT-1970", 10, "Section 3", "IPO", "http://ipo.gov.in", "c1")),
                List.of(new QuestionSource("IND-PAT-1970", 0.95)),
                Instant.now()
        );
        when(conversationService.getConversation(any(UserPrincipal.class), eq(convId)))
                .thenReturn(new ConversationDetailResponse(convId, "Patent Chat", Instant.now(), Instant.now(), List.of(msg)));

        mockMvc.perform(get("/api/v1/conversations/" + convId)
                        .header("X-Dev-User-Id", "test-user-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(convId.toString()))
                .andExpect(jsonPath("$.messages[0].citations[0].document").value("Patents Act 1970"))
                .andExpect(jsonPath("$.messages[0].citations[0].page").value(10));
    }

    @Test
    @DisplayName("Update conversation title")
    void testUpdateConversation() throws Exception {
        UUID convId = UUID.randomUUID();
        when(conversationService.updateConversation(any(UserPrincipal.class), eq(convId), any(UpdateConversationRequest.class)))
                .thenReturn(new ConversationSummaryResponse(convId, "Updated Title", Instant.now(), Instant.now()));

        mockMvc.perform(patch("/api/v1/conversations/" + convId)
                        .header("X-Dev-User-Id", "test-user-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Updated Title\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated Title"));
    }

    @Test
    @DisplayName("Delete conversation returns 204 No Content")
    void testDeleteConversation() throws Exception {
        UUID convId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/conversations/" + convId)
                        .header("X-Dev-User-Id", "test-user-1"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Ask question in conversation returns grounded response with evidence")
    void testAskInConversation() throws Exception {
        UUID convId = UUID.randomUUID();
        UUID msgId = UUID.randomUUID();
        UUID userMsgId = UUID.randomUUID();

        ConversationMessageResponse msgResponse = new ConversationMessageResponse(
                convId,
                msgId,
                userMsgId,
                "Form 1 is required.",
                "RAG_GROUNDED",
                0.93,
                false,
                Jurisdiction.INDIA,
                Language.EN,
                Language.EN,
                Language.EN,
                QuestionIntent.PATENT,
                List.of(new QuestionCitation("Patents Act", "doc-1", 5, "Sec 7", "IPO", "http://ipo.gov.in", "c-1")),
                List.of(new QuestionSource("doc-1", 0.93)),
                Instant.now()
        );

        when(conversationService.askInConversation(any(UserPrincipal.class), eq(convId), any(ConversationMessageRequest.class)))
                .thenReturn(msgResponse);

        mockMvc.perform(post("/api/v1/conversations/" + convId + "/messages")
                        .header("X-Dev-User-Id", "test-user-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "What is required for patent filing?",
                                  "jurisdiction": "INDIA",
                                  "language": "en"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversation_id").value(convId.toString()))
                .andExpect(jsonPath("$.message_id").value(msgId.toString()))
                .andExpect(jsonPath("$.answer").value("Form 1 is required."))
                .andExpect(jsonPath("$.response_type").value("RAG_GROUNDED"))
                .andExpect(jsonPath("$.citations[0].document").value("Patents Act"));
    }

    @Test
    @DisplayName("Ownership rejection returns 403 Forbidden")
    void testOwnershipRejection() throws Exception {
        UUID convId = UUID.randomUUID();
        when(conversationService.getConversation(any(UserPrincipal.class), eq(convId)))
                .thenThrow(new ConversationAccessDeniedException("You do not have access to this conversation."));

        mockMvc.perform(get("/api/v1/conversations/" + convId)
                        .header("X-Dev-User-Id", "test-user-1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("Conversation not found returns 404")
    void testConversationNotFound() throws Exception {
        UUID convId = UUID.randomUUID();
        when(conversationService.getConversation(any(UserPrincipal.class), eq(convId)))
                .thenThrow(new ConversationNotFoundException("Conversation not found with id: " + convId));

        mockMvc.perform(get("/api/v1/conversations/" + convId)
                        .header("X-Dev-User-Id", "test-user-1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CONVERSATION_NOT_FOUND"));
    }

    @Test
    @DisplayName("Unauthenticated request to conversations returns 401")
    void testUnauthenticatedRequest() throws Exception {
        when(securityProperties.isDevMode()).thenReturn(false);
        when(securityProperties.apiKeyRequired()).thenReturn(false);

        mockMvc.perform(get("/api/v1/conversations"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
}
