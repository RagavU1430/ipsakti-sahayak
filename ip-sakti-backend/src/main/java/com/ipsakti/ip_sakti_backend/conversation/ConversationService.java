package com.ipsakti.ip_sakti_backend.conversation;

import com.ipsakti.ip_sakti_backend.auth.UserPrincipal;
import com.ipsakti.ip_sakti_backend.auth.UserService;
import com.ipsakti.ip_sakti_backend.conversation.dto.ConversationDetailResponse;
import com.ipsakti.ip_sakti_backend.conversation.dto.ConversationMessageRequest;
import com.ipsakti.ip_sakti_backend.conversation.dto.ConversationMessageResponse;
import com.ipsakti.ip_sakti_backend.conversation.dto.ConversationPageResponse;
import com.ipsakti.ip_sakti_backend.conversation.dto.ConversationSummaryResponse;
import com.ipsakti.ip_sakti_backend.conversation.dto.CreateConversationRequest;
import com.ipsakti.ip_sakti_backend.conversation.dto.MessageDetailResponse;
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
import com.ipsakti.ip_sakti_backend.question.QuestionService;
import com.ipsakti.ip_sakti_backend.question.model.QuestionCitation;
import com.ipsakti.ip_sakti_backend.question.model.QuestionRequest;
import com.ipsakti.ip_sakti_backend.question.model.QuestionResponse;
import com.ipsakti.ip_sakti_backend.question.model.QuestionSource;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConversationService {

    private static final Logger log = LoggerFactory.getLogger(ConversationService.class);

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final MessageCitationRepository citationRepository;
    private final MessageSourceRepository sourceRepository;
    private final UserService userService;
    private final QuestionService questionService;

    public ConversationService(
            ConversationRepository conversationRepository,
            MessageRepository messageRepository,
            MessageCitationRepository citationRepository,
            MessageSourceRepository sourceRepository,
            UserService userService,
            QuestionService questionService
    ) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.citationRepository = citationRepository;
        this.sourceRepository = sourceRepository;
        this.userService = userService;
        this.questionService = questionService;
    }

    @Transactional
    public ConversationSummaryResponse createConversation(UserPrincipal principal, CreateConversationRequest request) {
        UserEntity user = resolveUser(principal);
        String title = (request != null && request.title() != null && !request.title().isBlank())
                ? request.title().trim()
                : "New Conversation";

        ConversationEntity conversation = new ConversationEntity(user, title);
        ConversationEntity saved = conversationRepository.save(conversation);

        log.info("conversation_created conversationId={} userId={}", saved.getId(), user.getId());
        return new ConversationSummaryResponse(saved.getId(), saved.getTitle(), saved.getCreatedAt(), saved.getUpdatedAt());
    }

    @Transactional(readOnly = true)
    public ConversationPageResponse listConversations(UserPrincipal principal, int page, int size) {
        if (page < 0) {
            page = 0;
        }
        if (size <= 0 || size > 100) {
            size = 20;
        }

        UserEntity user = resolveUser(principal);
        Pageable pageable = PageRequest.of(page, size);
        Page<ConversationEntity> pageResult = conversationRepository.findByUserOrderByUpdatedAtDesc(user, pageable);

        List<ConversationSummaryResponse> items = pageResult.getContent().stream()
                .map(c -> new ConversationSummaryResponse(c.getId(), c.getTitle(), c.getCreatedAt(), c.getUpdatedAt()))
                .toList();

        return new ConversationPageResponse(items, pageResult.getNumber(), pageResult.getSize(), pageResult.getTotalElements(), pageResult.getTotalPages());
    }

    @Transactional(readOnly = true)
    public ConversationDetailResponse getConversation(UserPrincipal principal, UUID conversationId) {
        ConversationEntity conversation = findAndVerifyOwnership(principal, conversationId);
        List<MessageEntity> messages = messageRepository.findByConversationOrderByCreatedAtAsc(conversation);

        List<MessageDetailResponse> messageResponses = messages.stream()
                .map(this::mapToMessageDetail)
                .toList();

        return new ConversationDetailResponse(
                conversation.getId(),
                conversation.getTitle(),
                conversation.getCreatedAt(),
                conversation.getUpdatedAt(),
                messageResponses
        );
    }

    @Transactional
    public ConversationSummaryResponse updateConversation(UserPrincipal principal, UUID conversationId, UpdateConversationRequest request) {
        ConversationEntity conversation = findAndVerifyOwnership(principal, conversationId);
        conversation.setTitle(request.title());
        conversation.setUpdatedAt(Instant.now());
        ConversationEntity updated = conversationRepository.save(conversation);

        log.info("conversation_updated conversationId={} userId={}", updated.getId(), principal.getId());
        return new ConversationSummaryResponse(updated.getId(), updated.getTitle(), updated.getCreatedAt(), updated.getUpdatedAt());
    }

    @Transactional
    public void deleteConversation(UserPrincipal principal, UUID conversationId) {
        ConversationEntity conversation = findAndVerifyOwnership(principal, conversationId);
        messageRepository.deleteByConversation(conversation);
        conversationRepository.delete(conversation);
        log.info("conversation_deleted conversationId={} userId={}", conversationId, principal.getId());
    }

    public ConversationMessageResponse askInConversation(UserPrincipal principal, UUID conversationId, ConversationMessageRequest request) {
        // Step 1: Verify conversation exists and user owns it, then persist user message
        UserMessagePersistenceResult userResult = persistUserMessage(principal, conversationId, request);

        // Step 2: Invoke existing RAG QuestionService outside long DB lock
        QuestionRequest questionRequest = new QuestionRequest(request.question(), request.jurisdiction(), request.language());
        QuestionResponse questionResponse = questionService.answer(questionRequest);

        // Step 3: Persist assistant message, citations, and sources
        return persistAssistantResponse(
                conversationId,
                userResult.userMessageId(),
                questionResponse
        );
    }

    @Transactional
    public UserMessagePersistenceResult persistUserMessage(UserPrincipal principal, UUID conversationId, ConversationMessageRequest request) {
        ConversationEntity conversation = findAndVerifyOwnership(principal, conversationId);

        String jurisdictionStr = (request.jurisdiction() != null) ? request.jurisdiction().name() : "AUTO";
        String languageStr = (request.language() != null) ? request.language().name().toLowerCase() : "en";

        MessageEntity userMessage = MessageEntity.userMessage(
                conversation,
                request.question(),
                jurisdictionStr,
                languageStr
        );
        MessageEntity savedUserMessage = messageRepository.save(userMessage);

        conversation.setUpdatedAt(Instant.now());
        conversationRepository.save(conversation);

        log.info("user_message_persisted conversationId={} messageId={}", conversationId, savedUserMessage.getId());
        return new UserMessagePersistenceResult(savedUserMessage.getId(), conversation);
    }

    @Transactional
    public ConversationMessageResponse persistAssistantResponse(
            UUID conversationId,
            UUID userMessageId,
            QuestionResponse response
    ) {
        ConversationEntity conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ConversationNotFoundException("Conversation not found with id: " + conversationId));

        MessageEntity assistantMessage = MessageEntity.assistantMessage(
                conversation,
                response.answer(),
                response.answerType() != null ? response.answerType().name() : null,
                response.confidence(),
                response.abstained(),
                response.jurisdiction() != null ? response.jurisdiction().name() : null,
                response.language() != null ? response.language().name().toLowerCase() : null,
                response.detectedLanguage() != null ? response.detectedLanguage().name().toLowerCase() : null,
                response.processingLanguage() != null ? response.processingLanguage().name().toLowerCase() : null,
                response.intent() != null ? response.intent().name() : null
        );
        MessageEntity savedAssistantMessage = messageRepository.save(assistantMessage);

        // Persist citations if present
        List<QuestionCitation> citations = response.citations() != null ? response.citations() : List.of();
        int citationOrdinal = 0;
        for (QuestionCitation c : citations) {
            MessageCitationEntity citationEntity = new MessageCitationEntity(
                    savedAssistantMessage,
                    c.document(),
                    c.documentId(),
                    c.page(),
                    c.section(),
                    c.authority(),
                    c.sourceUrl(),
                    c.chunkId(),
                    citationOrdinal++
            );
            savedAssistantMessage.addCitation(citationEntity);
            citationRepository.save(citationEntity);
        }

        // Persist sources if present
        List<QuestionSource> sources = response.sources() != null ? response.sources() : List.of();
        int sourceOrdinal = 0;
        for (QuestionSource s : sources) {
            MessageSourceEntity sourceEntity = new MessageSourceEntity(
                    savedAssistantMessage,
                    s.documentId(),
                    s.score(),
                    sourceOrdinal++
            );
            savedAssistantMessage.addSource(sourceEntity);
            sourceRepository.save(sourceEntity);
        }

        conversation.setUpdatedAt(Instant.now());
        conversationRepository.save(conversation);

        log.info("assistant_message_persisted conversationId={} assistantMessageId={} citationsCount={} sourcesCount={}",
                conversationId, savedAssistantMessage.getId(), citations.size(), sources.size());

        return new ConversationMessageResponse(
                conversationId,
                savedAssistantMessage.getId(),
                userMessageId,
                response.answer(),
                response.answerType() != null ? response.answerType().name() : null,
                response.confidence(),
                response.abstained(),
                response.jurisdiction(),
                response.language(),
                response.detectedLanguage(),
                response.processingLanguage(),
                response.intent(),
                citations,
                sources,
                savedAssistantMessage.getCreatedAt()
        );
    }

    private ConversationEntity findAndVerifyOwnership(UserPrincipal principal, UUID conversationId) {
        ConversationEntity conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ConversationNotFoundException("Conversation not found with id: " + conversationId));

        boolean isOwner = false;
        if (principal.getId() != null && conversation.getUser() != null && conversation.getUser().getId() != null) {
            isOwner = principal.getId().equals(conversation.getUser().getId());
        }
        if (!isOwner && principal.getExternalAuthId() != null && conversation.getUser() != null && conversation.getUser().getExternalAuthId() != null) {
            isOwner = principal.getExternalAuthId().equals(conversation.getUser().getExternalAuthId());
        }

        if (!isOwner) {
            log.warn("conversation_access_denied conversationId={} principalAuthId={} ownerAuthId={}",
                    conversationId, principal.getExternalAuthId(), conversation.getUser() != null ? conversation.getUser().getExternalAuthId() : null);
            throw new ConversationAccessDeniedException("You do not have access to this conversation.");
        }

        return conversation;
    }

    private UserEntity resolveUser(UserPrincipal principal) {
        if (principal.getId() != null) {
            return userService.findById(principal.getId())
                    .orElseGet(() -> userService.getOrCreateUser(principal.getExternalAuthId(), principal.getEmail(), "User"));
        }
        return userService.getOrCreateUser(principal.getExternalAuthId(), principal.getEmail(), "User");
    }

    private MessageDetailResponse mapToMessageDetail(MessageEntity message) {
        List<MessageCitationEntity> citationEntities = (message.getCitations() != null && !message.getCitations().isEmpty())
                ? message.getCitations()
                : citationRepository.findByMessageOrderByOrdinalAsc(message);

        List<QuestionCitation> citations = new ArrayList<>();
        if (citationEntities != null) {
            for (MessageCitationEntity c : citationEntities) {
                citations.add(new QuestionCitation(
                        c.getDocument(),
                        c.getDocumentId(),
                        c.getPage(),
                        c.getSection(),
                        c.getAuthority(),
                        c.getSourceUrl(),
                        c.getChunkId()
                ));
            }
        }

        List<MessageSourceEntity> sourceEntities = (message.getSources() != null && !message.getSources().isEmpty())
                ? message.getSources()
                : sourceRepository.findByMessageOrderByOrdinalAsc(message);

        List<QuestionSource> sources = new ArrayList<>();
        if (sourceEntities != null) {
            for (MessageSourceEntity s : sourceEntities) {
                sources.add(new QuestionSource(s.getDocumentId(), s.getScore()));
            }
        }

        return new MessageDetailResponse(
                message.getId(),
                message.getRole(),
                message.getContent(),
                message.getResponseType(),
                message.getConfidence(),
                message.getAbstained(),
                message.getJurisdiction(),
                message.getLanguage(),
                message.getDetectedLanguage(),
                message.getProcessingLanguage(),
                message.getIntent(),
                citations,
                sources,
                message.getCreatedAt()
        );
    }

    public record UserMessagePersistenceResult(UUID userMessageId, ConversationEntity conversation) {}
}
