package com.ipsakti.ip_sakti_backend.conversation;

import com.ipsakti.ip_sakti_backend.auth.UserPrincipal;
import com.ipsakti.ip_sakti_backend.auth.UserService;
import com.ipsakti.ip_sakti_backend.conversation.dto.ConversationDetailResponse;
import com.ipsakti.ip_sakti_backend.conversation.dto.ConversationMessageRequest;
import com.ipsakti.ip_sakti_backend.conversation.dto.ConversationMessageResponse;
import com.ipsakti.ip_sakti_backend.conversation.dto.ConversationPageResponse;
import com.ipsakti.ip_sakti_backend.conversation.dto.ConversationSummaryResponse;
import com.ipsakti.ip_sakti_backend.conversation.dto.CreateConversationRequest;
import com.ipsakti.ip_sakti_backend.conversation.dto.UpdateConversationRequest;
import com.ipsakti.ip_sakti_backend.conversation.entity.ConversationEntity;
import com.ipsakti.ip_sakti_backend.conversation.entity.MessageCitationEntity;
import com.ipsakti.ip_sakti_backend.conversation.entity.MessageEntity;
import com.ipsakti.ip_sakti_backend.conversation.entity.MessageSourceEntity;
import com.ipsakti.ip_sakti_backend.conversation.entity.UserEntity;
import com.ipsakti.ip_sakti_backend.conversation.repository.ConversationRepository;
import com.ipsakti.ip_sakti_backend.conversation.repository.MessageCitationRepository;
import com.ipsakti.ip_sakti_backend.conversation.repository.MessageRepository;
import com.ipsakti.ip_sakti_backend.conversation.repository.MessageSourceRepository;
import com.ipsakti.ip_sakti_backend.exception.ConversationAccessDeniedException;
import com.ipsakti.ip_sakti_backend.exception.ConversationNotFoundException;
import com.ipsakti.ip_sakti_backend.exception.RagClientException;
import com.ipsakti.ip_sakti_backend.question.QuestionService;
import com.ipsakti.ip_sakti_backend.question.model.AnswerType;
import com.ipsakti.ip_sakti_backend.question.model.Jurisdiction;
import com.ipsakti.ip_sakti_backend.question.model.Language;
import com.ipsakti.ip_sakti_backend.question.model.QuestionCitation;
import com.ipsakti.ip_sakti_backend.question.model.QuestionIntent;
import com.ipsakti.ip_sakti_backend.question.model.QuestionRequest;
import com.ipsakti.ip_sakti_backend.question.model.QuestionResponse;
import com.ipsakti.ip_sakti_backend.question.model.QuestionSource;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private MessageCitationRepository citationRepository;

    @Mock
    private MessageSourceRepository sourceRepository;

    @Mock
    private UserService userService;

    @Mock
    private QuestionService questionService;

    private ConversationService conversationService;

    private UserEntity userA;
    private UserPrincipal principalA;
    private UserPrincipal principalB;

    @BeforeEach
    void setUp() {
        conversationService = new ConversationService(
                conversationRepository,
                messageRepository,
                citationRepository,
                sourceRepository,
                userService,
                questionService
        );

        UUID userAId = UUID.randomUUID();
        userA = new UserEntity("auth-user-a", "usera@example.com", "User A");
        userA.setId(userAId);
        principalA = UserPrincipal.of(userAId, "auth-user-a", "usera@example.com");

        UUID userBId = UUID.randomUUID();
        principalB = UserPrincipal.of(userBId, "auth-user-b", "userb@example.com");
    }

    @Test
    @DisplayName("Create conversation sets user and default title")
    void testCreateConversation() {
        when(userService.findById(principalA.getId())).thenReturn(Optional.of(userA));
        when(conversationRepository.save(any(ConversationEntity.class))).thenAnswer(invocation -> {
            ConversationEntity c = invocation.getArgument(0);
            c.setId(UUID.randomUUID());
            c.setCreatedAt(Instant.now());
            c.setUpdatedAt(Instant.now());
            return c;
        });

        ConversationSummaryResponse response = conversationService.createConversation(principalA, new CreateConversationRequest("Patent Queries"));

        assertThat(response).isNotNull();
        assertThat(response.id()).isNotNull();
        assertThat(response.title()).isEqualTo("Patent Queries");
    }

    @Test
    @DisplayName("List conversations returns paginated results for user")
    void testListConversations() {
        when(userService.findById(principalA.getId())).thenReturn(Optional.of(userA));

        ConversationEntity c1 = new ConversationEntity(userA, "Conv 1");
        c1.setId(UUID.randomUUID());
        c1.setCreatedAt(Instant.now());
        c1.setUpdatedAt(Instant.now());

        Page<ConversationEntity> page = new PageImpl<>(List.of(c1));
        when(conversationRepository.findByUserOrderByUpdatedAtDesc(eq(userA), any(Pageable.class))).thenReturn(page);

        ConversationPageResponse response = conversationService.listConversations(principalA, 0, 20);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).title()).isEqualTo("Conv 1");
        assertThat(response.totalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("Get conversation returns full details with messages and citations for owner")
    void testGetConversationSuccess() {
        UUID convId = UUID.randomUUID();
        ConversationEntity conv = new ConversationEntity(userA, "Conv Title");
        conv.setId(convId);
        conv.setCreatedAt(Instant.now());
        conv.setUpdatedAt(Instant.now());

        MessageEntity userMsg = MessageEntity.userMessage(conv, "How to file patent?", "INDIA", "en");
        userMsg.setId(UUID.randomUUID());
        userMsg.setCreatedAt(Instant.now());

        MessageEntity assistantMsg = MessageEntity.assistantMessage(
                conv, "File Form 1.", "RAG_GROUNDED", 0.95, false, "INDIA", "en", "en", "en", "PRODUCT_QUESTION"
        );
        assistantMsg.setId(UUID.randomUUID());
        assistantMsg.setCreatedAt(Instant.now());
        MessageCitationEntity citation = new MessageCitationEntity(assistantMsg, "Patents Act", "doc-1", 12, "Sec 7", "CGPDTM", "https://ipindia.gov.in", "chunk-1", 0);
        assistantMsg.addCitation(citation);
        MessageSourceEntity source = new MessageSourceEntity(assistantMsg, "doc-1", 0.92, 0);
        assistantMsg.addSource(source);

        when(conversationRepository.findById(convId)).thenReturn(Optional.of(conv));
        when(messageRepository.findByConversationOrderByCreatedAtAsc(conv)).thenReturn(List.of(userMsg, assistantMsg));

        ConversationDetailResponse detail = conversationService.getConversation(principalA, convId);

        assertThat(detail.id()).isEqualTo(convId);
        assertThat(detail.title()).isEqualTo("Conv Title");
        assertThat(detail.messages()).hasSize(2);
        assertThat(detail.messages().get(0).role()).isEqualTo("user");
        assertThat(detail.messages().get(1).role()).isEqualTo("assistant");
        assertThat(detail.messages().get(1).citations()).hasSize(1);
        assertThat(detail.messages().get(1).citations().get(0).document()).isEqualTo("Patents Act");
        assertThat(detail.messages().get(1).sources()).hasSize(1);
    }

    @Test
    @DisplayName("User B cannot access User A's conversation")
    void testOwnershipIsolation() {
        UUID convId = UUID.randomUUID();
        ConversationEntity conv = new ConversationEntity(userA, "User A Secret Conv");
        conv.setId(convId);

        when(conversationRepository.findById(convId)).thenReturn(Optional.of(conv));

        assertThatThrownBy(() -> conversationService.getConversation(principalB, convId))
                .isInstanceOf(ConversationAccessDeniedException.class);

        assertThatThrownBy(() -> conversationService.updateConversation(principalB, convId, new UpdateConversationRequest("New Title")))
                .isInstanceOf(ConversationAccessDeniedException.class);

        assertThatThrownBy(() -> conversationService.deleteConversation(principalB, convId))
                .isInstanceOf(ConversationAccessDeniedException.class);

        assertThatThrownBy(() -> conversationService.askInConversation(principalB, convId, new ConversationMessageRequest("Question", Jurisdiction.INDIA, Language.EN)))
                .isInstanceOf(ConversationAccessDeniedException.class);
    }

    @Test
    @DisplayName("Throws ConversationNotFoundException when conversation does not exist")
    void testConversationNotFound() {
        UUID convId = UUID.randomUUID();
        when(conversationRepository.findById(convId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> conversationService.getConversation(principalA, convId))
                .isInstanceOf(ConversationNotFoundException.class);
    }

    @Test
    @DisplayName("Ask in conversation persists user message, executes RAG, and persists assistant response with evidence")
    void testAskInConversationGroundedFlow() {
        UUID convId = UUID.randomUUID();
        ConversationEntity conv = new ConversationEntity(userA, "Patent Chat");
        conv.setId(convId);

        UUID userMsgId = UUID.randomUUID();
        when(conversationRepository.findById(convId)).thenReturn(Optional.of(conv));
        when(messageRepository.save(any(MessageEntity.class))).thenAnswer(invocation -> {
            MessageEntity m = invocation.getArgument(0);
            if ("user".equals(m.getRole())) {
                m.setId(userMsgId);
            } else {
                m.setId(UUID.randomUUID());
            }
            m.setCreatedAt(Instant.now());
            return m;
        });

        QuestionResponse ragResponse = new QuestionResponse(
                "Patent filing requires Form 1 and Form 2.",
                AnswerType.RAG_GROUNDED,
                0.94,
                false,
                Jurisdiction.INDIA,
                Language.EN,
                Language.EN,
                Language.EN,
                QuestionIntent.PATENT,
                List.of(new QuestionCitation("Patents Act 1970", "doc-patents", 15, "Section 7", "IPO", "http://ipo.gov.in", "chunk-7")),
                List.of(new QuestionSource("doc-patents", 0.94))
        );
        when(questionService.answer(any(QuestionRequest.class))).thenReturn(ragResponse);

        ConversationMessageRequest msgRequest = new ConversationMessageRequest("What forms are required for patent filing?", Jurisdiction.INDIA, Language.EN);
        ConversationMessageResponse response = conversationService.askInConversation(principalA, convId, msgRequest);

        assertThat(response.conversationId()).isEqualTo(convId);
        assertThat(response.answer()).isEqualTo("Patent filing requires Form 1 and Form 2.");
        assertThat(response.responseType()).isEqualTo("RAG_GROUNDED");
        assertThat(response.confidence()).isEqualTo(0.94);
        assertThat(response.abstained()).isFalse();
        assertThat(response.citations()).hasSize(1);
        assertThat(response.sources()).hasSize(1);

        verify(citationRepository).save(any(MessageCitationEntity.class));
        verify(sourceRepository).save(any(MessageSourceEntity.class));
    }

    @Test
    @DisplayName("Ask in conversation persists abstained responses accurately")
    void testAskInConversationAbstainedFlow() {
        UUID convId = UUID.randomUUID();
        ConversationEntity conv = new ConversationEntity(userA, "Patent Chat");
        conv.setId(convId);

        when(conversationRepository.findById(convId)).thenReturn(Optional.of(conv));
        when(messageRepository.save(any(MessageEntity.class))).thenAnswer(invocation -> {
            MessageEntity m = invocation.getArgument(0);
            m.setId(UUID.randomUUID());
            m.setCreatedAt(Instant.now());
            return m;
        });

        QuestionResponse ragResponse = new QuestionResponse(
                "I do not have sufficient legal evidence to answer this question reliably.",
                AnswerType.ABSTAINED,
                0.15,
                true,
                Jurisdiction.INDIA,
                Language.EN,
                Language.EN,
                Language.EN,
                QuestionIntent.GENERAL,
                List.of(),
                List.of()
        );
        when(questionService.answer(any(QuestionRequest.class))).thenReturn(ragResponse);

        ConversationMessageRequest msgRequest = new ConversationMessageRequest("What is quantum entanglement in patents?", Jurisdiction.INDIA, Language.EN);
        ConversationMessageResponse response = conversationService.askInConversation(principalA, convId, msgRequest);

        assertThat(response.abstained()).isTrue();
        assertThat(response.responseType()).isEqualTo("ABSTAINED");
        assertThat(response.confidence()).isEqualTo(0.15);
        assertThat(response.citations()).isEmpty();
        assertThat(response.sources()).isEmpty();
    }

    @Test
    @DisplayName("RAG failure leaves user message saved and does not create fake assistant response")
    void testAskInConversationRagFailure() {
        UUID convId = UUID.randomUUID();
        ConversationEntity conv = new ConversationEntity(userA, "Patent Chat");
        conv.setId(convId);

        when(conversationRepository.findById(convId)).thenReturn(Optional.of(conv));
        when(messageRepository.save(any(MessageEntity.class))).thenAnswer(invocation -> {
            MessageEntity m = invocation.getArgument(0);
            m.setId(UUID.randomUUID());
            m.setCreatedAt(Instant.now());
            return m;
        });

        when(questionService.answer(any(QuestionRequest.class)))
                .thenThrow(RagClientException.unavailable());

        ConversationMessageRequest msgRequest = new ConversationMessageRequest("What is patent?", Jurisdiction.INDIA, Language.EN);

        assertThatThrownBy(() -> conversationService.askInConversation(principalA, convId, msgRequest))
                .isInstanceOf(RagClientException.class);

        // Verify user message was saved
        verify(messageRepository).save(any(MessageEntity.class));
        // Verify no citations were saved
        verify(citationRepository, never()).save(any(MessageCitationEntity.class));
    }
}
