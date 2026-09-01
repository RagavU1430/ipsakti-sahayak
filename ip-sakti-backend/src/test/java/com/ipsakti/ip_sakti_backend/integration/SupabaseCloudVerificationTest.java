package com.ipsakti.ip_sakti_backend.integration;

import com.ipsakti.ip_sakti_backend.auth.UserPrincipal;
import com.ipsakti.ip_sakti_backend.auth.UserService;
import com.ipsakti.ip_sakti_backend.config.JacksonConfig;
import com.ipsakti.ip_sakti_backend.conversation.ConversationService;
import com.ipsakti.ip_sakti_backend.conversation.dto.ConversationDetailResponse;
import com.ipsakti.ip_sakti_backend.conversation.dto.ConversationMessageRequest;
import com.ipsakti.ip_sakti_backend.conversation.dto.ConversationMessageResponse;
import com.ipsakti.ip_sakti_backend.conversation.dto.ConversationPageResponse;
import com.ipsakti.ip_sakti_backend.conversation.dto.ConversationSummaryResponse;
import com.ipsakti.ip_sakti_backend.conversation.dto.CreateConversationRequest;
import com.ipsakti.ip_sakti_backend.conversation.dto.UpdateConversationRequest;
import com.ipsakti.ip_sakti_backend.conversation.entity.UserEntity;
import com.ipsakti.ip_sakti_backend.conversation.repository.ConversationRepository;
import com.ipsakti.ip_sakti_backend.conversation.repository.UserRepository;
import com.ipsakti.ip_sakti_backend.exception.ConversationAccessDeniedException;
import com.ipsakti.ip_sakti_backend.exception.ConversationNotFoundException;
import com.ipsakti.ip_sakti_backend.question.model.Language;
import com.ipsakti.ip_sakti_backend.rag.RagClient;
import com.ipsakti.ip_sakti_backend.rag.dto.RagAskResponse;
import com.ipsakti.ip_sakti_backend.rag.dto.RagCitation;
import com.ipsakti.ip_sakti_backend.rag.dto.RagSource;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@Import(JacksonConfig.class)
class SupabaseCloudVerificationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private ConversationService conversationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @MockitoBean
    private RagClient ragClient;

    private UserPrincipal userA;
    private UserPrincipal userB;
    private UUID createdConversationId;
    private String extA;
    private String extB;

    @BeforeEach
    void setUp() {
        extA = "test_sb_user_a_" + UUID.randomUUID().toString().substring(0, 8);
        extB = "test_sb_user_b_" + UUID.randomUUID().toString().substring(0, 8);

        UserEntity entityA = userService.getOrCreateUser(extA, "userA@supabase-test.com", "Test User A");
        UserEntity entityB = userService.getOrCreateUser(extB, "userB@supabase-test.com", "Test User B");

        userA = new UserPrincipal(entityA.getId(), extA, "userA@supabase-test.com", List.of(new SimpleGrantedAuthority("ROLE_USER")));
        userB = new UserPrincipal(entityB.getId(), extB, "userB@supabase-test.com", List.of(new SimpleGrantedAuthority("ROLE_USER")));

        when(ragClient.ask(any())).thenReturn(new RagAskResponse(
                "Trademark registration in India is governed by Section 9 and 11 of Trade Marks Act 1999.",
                0.93,
                false,
                List.of(new RagCitation("The Trade Marks Act, 1999", "IND-TM-ACT-1999", 11, "Section 9", "IP India", "https://ipindia.gov.in", "chunk-tm-1")),
                List.of(new RagSource("IND-TM-ACT-1999", 0.93))
        ));
    }

    @AfterEach
    void tearDown() {
        if (createdConversationId != null) {
            try {
                conversationService.deleteConversation(userA, createdConversationId);
            } catch (Exception ignored) {
            }
        }
        userRepository.findByExternalAuthId(extA).ifPresent(userRepository::delete);
        userRepository.findByExternalAuthId(extB).ifPresent(userRepository::delete);
    }

    @Test
    @DisplayName("Supabase Cloud Verification: User & Conversation Persistence with Citations, Sources, Ownership Isolation & Cascade Deletion")
    void testSupabaseCloudFullLifecycle() {
        // 1. Create Conversation for User A
        ConversationSummaryResponse createdConv = conversationService.createConversation(
                userA, new CreateConversationRequest("Supabase Cloud Verification")
        );
        createdConversationId = createdConv.id();
        assertThat(createdConv).isNotNull();
        assertThat(createdConv.title()).isEqualTo("Supabase Cloud Verification");

        // 2. Post Message to Conversation
        ConversationMessageResponse msgResponse = conversationService.askInConversation(
                userA,
                createdConversationId,
                new ConversationMessageRequest("What are the requirements for registering a trademark in India?", null, Language.EN)
        );
        assertThat(msgResponse).isNotNull();
        assertThat(msgResponse.answer()).contains("Trade Marks Act 1999");
        assertThat(msgResponse.citations()).hasSize(1);
        assertThat(msgResponse.citations().get(0).documentId()).isEqualTo("IND-TM-ACT-1999");
        assertThat(msgResponse.sources()).hasSize(1);
        assertThat(msgResponse.sources().get(0).documentId()).isEqualTo("IND-TM-ACT-1999");

        // 3. Read Conversation with full transcript
        ConversationDetailResponse detail = conversationService.getConversation(userA, createdConversationId);
        assertThat(detail).isNotNull();
        assertThat(detail.messages()).hasSize(2);
        assertThat(detail.messages().get(0).role()).isEqualTo("user");
        assertThat(detail.messages().get(1).role()).isEqualTo("assistant");
        assertThat(detail.messages().get(1).citations()).hasSize(1);
        assertThat(detail.messages().get(1).sources()).hasSize(1);

        // 4. Update Conversation Title
        ConversationSummaryResponse updatedConv = conversationService.updateConversation(
                userA, createdConversationId, new UpdateConversationRequest("Trademark IP Strategy")
        );
        assertThat(updatedConv.title()).isEqualTo("Trademark IP Strategy");

        // 5. List Conversations with pagination
        ConversationPageResponse page = conversationService.listConversations(userA, 0, 10);
        assertThat(page.items()).hasSize(1);
        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.items().get(0).id()).isEqualTo(createdConversationId);

        // 6. Multi-Tenant Ownership Isolation: User B attempted access MUST fail
        assertThatThrownBy(() -> conversationService.getConversation(userB, createdConversationId))
                .isInstanceOf(ConversationAccessDeniedException.class);

        assertThatThrownBy(() -> conversationService.updateConversation(userB, createdConversationId, new UpdateConversationRequest("Hacked")))
                .isInstanceOf(ConversationAccessDeniedException.class);

        assertThatThrownBy(() -> conversationService.askInConversation(userB, createdConversationId, new ConversationMessageRequest("Hacked query", null, Language.EN)))
                .isInstanceOf(ConversationAccessDeniedException.class);

        assertThatThrownBy(() -> conversationService.deleteConversation(userB, createdConversationId))
                .isInstanceOf(ConversationAccessDeniedException.class);

        // 7. Cascade Deletion
        conversationService.deleteConversation(userA, createdConversationId);
        assertThatThrownBy(() -> conversationService.getConversation(userA, createdConversationId))
                .isInstanceOf(ConversationNotFoundException.class);

        createdConversationId = null;
    }
}
