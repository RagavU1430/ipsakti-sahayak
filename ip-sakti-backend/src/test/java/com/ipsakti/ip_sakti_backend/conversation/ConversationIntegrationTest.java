package com.ipsakti.ip_sakti_backend.conversation;

import com.ipsakti.ip_sakti_backend.auth.JwtService;
import com.ipsakti.ip_sakti_backend.auth.UserPrincipal;
import com.ipsakti.ip_sakti_backend.auth.UserService;
import com.ipsakti.ip_sakti_backend.conversation.dto.ConversationDetailResponse;
import com.ipsakti.ip_sakti_backend.conversation.dto.ConversationMessageRequest;
import com.ipsakti.ip_sakti_backend.conversation.dto.ConversationMessageResponse;
import com.ipsakti.ip_sakti_backend.conversation.dto.ConversationPageResponse;
import com.ipsakti.ip_sakti_backend.conversation.dto.ConversationSummaryResponse;
import com.ipsakti.ip_sakti_backend.conversation.dto.CreateConversationRequest;
import com.ipsakti.ip_sakti_backend.conversation.dto.UpdateConversationRequest;
import com.ipsakti.ip_sakti_backend.conversation.entity.UserEntity;
import com.ipsakti.ip_sakti_backend.exception.ConversationAccessDeniedException;
import com.ipsakti.ip_sakti_backend.question.model.Jurisdiction;
import com.ipsakti.ip_sakti_backend.question.model.Language;
import com.ipsakti.ip_sakti_backend.rag.RagClient;
import com.ipsakti.ip_sakti_backend.rag.dto.RagAskResponse;
import com.ipsakti.ip_sakti_backend.rag.dto.RagCitation;
import com.ipsakti.ip_sakti_backend.rag.dto.RagSource;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "rag.base-url=http://localhost:8000",
        "app.security.mode=dev"
})
@Transactional
class ConversationIntegrationTest {

    @Autowired
    private ConversationService conversationService;

    @Autowired
    private UserService userService;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private RagClient ragClient;

    private UserPrincipal userPrincipalA;
    private UserPrincipal userPrincipalB;

    @BeforeEach
    void setUp() {
        UserEntity userA = userService.getOrCreateUser("integ-user-a", "usera@integration.test", "User A");
        UserEntity userB = userService.getOrCreateUser("integ-user-b", "userb@integration.test", "User B");

        userPrincipalA = UserPrincipal.of(userA.getId(), userA.getExternalAuthId(), userA.getEmail());
        userPrincipalB = UserPrincipal.of(userB.getId(), userB.getExternalAuthId(), userB.getEmail());
    }

    @Test
    @DisplayName("End-to-End Conversation lifecycle with RAG evidence persistence")
    void testEndToEndConversationFlow() {
        // 1. Create conversation
        ConversationSummaryResponse created = conversationService.createConversation(
                userPrincipalA,
                new CreateConversationRequest("Ayurveda IP Queries")
        );
        UUID convId = created.id();
        assertThat(convId).isNotNull();
        assertThat(created.title()).isEqualTo("Ayurveda IP Queries");

        // 2. Mock RAG response
        when(ragClient.ask(any())).thenReturn(new RagAskResponse(
                "Ayurvedic formulations require prior art searching in TKDL and compliance with Section 3(p) of the Patents Act.",
                0.96,
                false,
                List.of(
                        new RagCitation("Patents Act 1970", "IND-PAT-1970", 8, "Section 3(p)", "IPO", "https://ipindia.gov.in", "chunk-101"),
                        new RagCitation("TKDL Guidelines", "TKDL-GUI-2020", 3, "Guideline 2", "CSIR", "https://tkdl.res.in", "chunk-202")
                ),
                List.of(
                        new RagSource("IND-PAT-1970", 0.96),
                        new RagSource("TKDL-GUI-2020", 0.91)
                )
        ));

        // 3. Ask question inside conversation
        ConversationMessageResponse msgResponse = conversationService.askInConversation(
                userPrincipalA,
                convId,
                new ConversationMessageRequest("Can I patent an Ayurvedic herbal formulation?", Jurisdiction.INDIA, Language.EN)
        );

        assertThat(msgResponse.conversationId()).isEqualTo(convId);
        assertThat(msgResponse.messageId()).isNotNull();
        assertThat(msgResponse.userMessageId()).isNotNull();
        assertThat(msgResponse.answer()).contains("TKDL");
        assertThat(msgResponse.responseType()).isEqualTo("RAG_GROUNDED");
        assertThat(msgResponse.confidence()).isEqualTo(0.96);
        assertThat(msgResponse.abstained()).isFalse();
        assertThat(msgResponse.citations()).hasSize(2);
        assertThat(msgResponse.citations().get(0).documentId()).isEqualTo("IND-PAT-1970");
        assertThat(msgResponse.citations().get(1).documentId()).isEqualTo("TKDL-GUI-2020");
        assertThat(msgResponse.sources()).hasSize(2);

        // 4. Retrieve conversation and verify persisted messages & evidence
        ConversationDetailResponse detail = conversationService.getConversation(userPrincipalA, convId);
        assertThat(detail.id()).isEqualTo(convId);
        assertThat(detail.messages()).hasSize(2);

        // First message: User
        assertThat(detail.messages().get(0).role()).isEqualTo("user");
        assertThat(detail.messages().get(0).content()).isEqualTo("Can I patent an Ayurvedic herbal formulation?");

        // Second message: Assistant
        assertThat(detail.messages().get(1).role()).isEqualTo("assistant");
        assertThat(detail.messages().get(1).content()).contains("TKDL");
        assertThat(detail.messages().get(1).citations()).hasSize(2);
        assertThat(detail.messages().get(1).citations().get(0).document()).isEqualTo("Patents Act 1970");
        assertThat(detail.messages().get(1).citations().get(0).page()).isEqualTo(8);
        assertThat(detail.messages().get(1).sources()).hasSize(2);

        // 5. Update conversation title
        ConversationSummaryResponse updated = conversationService.updateConversation(
                userPrincipalA,
                convId,
                new UpdateConversationRequest("Ayurveda IP & Patenting")
        );
        assertThat(updated.title()).isEqualTo("Ayurveda IP & Patenting");

        // 6. List conversations for User A
        ConversationPageResponse pageResponse = conversationService.listConversations(userPrincipalA, 0, 10);
        assertThat(pageResponse.items()).hasSize(1);
        assertThat(pageResponse.items().get(0).id()).isEqualTo(convId);
        assertThat(pageResponse.items().get(0).title()).isEqualTo("Ayurveda IP & Patenting");

        // 7. Ownership check: User B cannot view User A's conversation
        assertThatThrownBy(() -> conversationService.getConversation(userPrincipalB, convId))
                .isInstanceOf(ConversationAccessDeniedException.class);

        // User B's own conversation list is empty
        ConversationPageResponse userBPage = conversationService.listConversations(userPrincipalB, 0, 10);
        assertThat(userBPage.items()).isEmpty();

        // 8. Delete conversation
        conversationService.deleteConversation(userPrincipalA, convId);
        ConversationPageResponse pageAfterDelete = conversationService.listConversations(userPrincipalA, 0, 10);
        assertThat(pageAfterDelete.items()).isEmpty();
    }

    @Test
    @DisplayName("Abstained responses persist abstained=true and empty evidence in conversation")
    void testAbstainedPersistenceIntegration() {
        ConversationSummaryResponse conv = conversationService.createConversation(
                userPrincipalA,
                new CreateConversationRequest("Abstention Test")
        );

        when(ragClient.ask(any())).thenReturn(new RagAskResponse(
                "I do not have sufficient legal evidence to answer this question reliably.",
                0.12,
                true,
                List.of(),
                List.of()
        ));

        ConversationMessageResponse msgResponse = conversationService.askInConversation(
                userPrincipalA,
                conv.id(),
                new ConversationMessageRequest("What is the quantum state of IP law?", Jurisdiction.INDIA, Language.EN)
        );

        assertThat(msgResponse.abstained()).isTrue();
        assertThat(msgResponse.responseType()).isEqualTo("ABSTAINED");
        assertThat(msgResponse.confidence()).isEqualTo(0.12);
        assertThat(msgResponse.citations()).isEmpty();
        assertThat(msgResponse.sources()).isEmpty();

        // Verify retrieval matches persisted state
        ConversationDetailResponse detail = conversationService.getConversation(userPrincipalA, conv.id());
        assertThat(detail.messages()).hasSize(2);
        assertThat(detail.messages().get(1).abstained()).isTrue();
        assertThat(detail.messages().get(1).responseType()).isEqualTo("ABSTAINED");
        assertThat(detail.messages().get(1).citations()).isEmpty();
    }
}
