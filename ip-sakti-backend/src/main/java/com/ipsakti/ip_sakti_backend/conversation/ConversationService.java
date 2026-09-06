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
import com.ipsakti.ip_sakti_backend.question.routing.QueryDomain;
import com.ipsakti.ip_sakti_backend.question.routing.QueryRoute;
import com.ipsakti.ip_sakti_backend.question.routing.RoutingContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
        List<MessageEntity> messages = messageRepository.findByConversationOrderByCreatedAtAsc(conversation);
        if (!messages.isEmpty()) {
            messageRepository.deleteAll(messages);
        }
        conversationRepository.delete(conversation);
        conversationRepository.flush();
        log.info("conversation_deleted conversationId={} userId={}", conversationId, principal.getId());
    }

    public ConversationMessageResponse askInConversation(UserPrincipal principal, UUID conversationId, ConversationMessageRequest request) {
        UserMessagePersistenceResult userResult = persistUserMessage(principal, conversationId, request);
        QuestionResponse questionResponse;
        try {
            RoutingContext context = routingContext(userResult.conversation());
            String effectiveQuestion = contextualizeReferentialFollowUp(request.question(), context);
            QuestionRequest questionRequest = new QuestionRequest(effectiveQuestion, request.jurisdiction(), request.language());
            questionResponse = context.previousRoute() == null
                    ? questionService.answer(questionRequest)
                    : questionService.answer(questionRequest, context);
        } catch (Exception e) {
            // Compensation: remove orphan user message if RAG fails
            try {
                messageRepository.deleteById(userResult.userMessageId());
                log.warn("rag_failed_orphan_user_message_removed conversationId={} messageId={} error={}", conversationId, userResult.userMessageId(), e.getMessage());
            } catch (Exception cleanupEx) {
                log.error("failed_to_cleanup_orphan_message", cleanupEx);
            }
            throw e;
        }
        return persistAssistantResponse(principal, conversationId, userResult.userMessageId(), questionResponse);
    }

    static String contextualizeReferentialFollowUp(String question, RoutingContext context) {
        if (question == null || context == null || context.previousDomain() == null) return question;

        String normalized = question.strip().toLowerCase(Locale.ROOT);
        boolean referential = List.of(
                " it ", " this ", " that ", " they ", " them ", " its ", "how long", "what about",
                "यह", "वह", "इसे", "उसका", "कितने समय", "कब तक",
                "அது", "இது", "எவ்வளவு காலம்",
                "అది", "ఇది", "ఎంత కాలం",
                "ಅದು", "ಇದು", "ಎಷ್ಟು ಕಾಲ",
                "അത്", "ഇത്", "എത്ര കാലം"
        ).stream().anyMatch(marker -> (" " + normalized + " ").contains(marker));

        if (!referential) return question;

        String topic = switch (context.previousDomain()) {
            case GEOGRAPHICAL_INDICATION -> "Geographical Indication";
            case INDUSTRIAL_DESIGN -> "Industrial Design";
            case TRADE_SECRET -> "Trade Secret";
            case TRADITIONAL_KNOWLEDGE -> "Traditional Knowledge";
            case BIODIVERSITY -> "Biological Diversity";
            case ABS -> "Access and Benefit Sharing (ABS)";
            case GRATK -> "GRATK";
            case INDIA_IP_LAW -> "Indian IP law";
            case INTERNATIONAL_IP -> "International IP";
            default -> context.previousDomain().name().replace('_', ' ');
        };
        return question + "\nContext for this referential follow-up: " + topic + ".";
    }

    private RoutingContext routingContext(ConversationEntity conversation) {
        List<MessageEntity> messages = messageRepository.findByConversationOrderByCreatedAtAsc(conversation);
        for (int index = messages.size() - 1; index >= 0; index--) {
            MessageEntity message = messages.get(index);
            if (!"assistant".equalsIgnoreCase(message.getRole())) continue;
            QueryRoute route = "GENERAL_FALLBACK".equalsIgnoreCase(message.getResponseType())
                    ? QueryRoute.GENERAL : QueryRoute.DOMAIN_RAG;
            QueryDomain domain = domainFromIntent(message.getIntent());
            return new RoutingContext(route, domain);
        }
        return RoutingContext.empty();
    }

    private QueryDomain domainFromIntent(String intent) {
        if (intent == null) return null;
        return switch (intent) {
            case "PATENT" -> QueryDomain.PATENT;
            case "TRADEMARK" -> QueryDomain.TRADEMARK;
            case "COPYRIGHT" -> QueryDomain.COPYRIGHT;
            case "DESIGN" -> QueryDomain.INDUSTRIAL_DESIGN;
            case "GI" -> QueryDomain.GEOGRAPHICAL_INDICATION;
            case "BIODIVERSITY_ABS" -> QueryDomain.ABS;
            case "AYURVEDA_REGULATION" -> QueryDomain.AYURVEDA;
            case "INTERNATIONAL_IP" -> QueryDomain.INTERNATIONAL_IP;
            case "IP_GENERAL", "PLANT_VARIETY" -> QueryDomain.IP;
            default -> null;
        };
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

        if ("New Conversation".equalsIgnoreCase(conversation.getTitle()) && request.question() != null) {
            String autoTitle = generateTitleFromQuestion(request.question());
            if (autoTitle != null && !autoTitle.isBlank()) {
                conversation.setTitle(autoTitle);
            }
        }

        conversation.setUpdatedAt(Instant.now());
        conversationRepository.save(conversation);

        log.info("user_message_persisted conversationId={} messageId={} title={}", conversationId, savedUserMessage.getId(), conversation.getTitle());
        return new UserMessagePersistenceResult(savedUserMessage.getId(), conversation);
    }

    private String generateTitleFromQuestion(String question) {
        if (question == null || question.isBlank()) return "New Conversation";
        String trimmed = question.trim();
        String lower = trimmed.toLowerCase(java.util.Locale.ROOT);
        if (lower.equals("hi") || lower.equals("hello") || lower.equals("hey")
                || lower.startsWith("hi ") || lower.startsWith("hello ") || lower.startsWith("hey ")) {
            return "New Conversation";
        }
        if (lower.contains("section 3(p)") || lower.contains("section 3p")) {
            return "Section 3(p)";
        }
        if (lower.contains("section 377")) {
            return "Section 377";
        }
        if (lower.contains("traditional knowledge")) {
            return "Traditional Knowledge";
        }
        if (lower.contains("ayurveda") || lower.contains("ayurvedic")) {
            return "Ayurveda Information";
        }
        if (lower.contains("access and benefit sharing") || lower.matches("(?i).*\\babs\\b.*")) {
            return "Access & Benefit Sharing";
        }
        if (lower.contains("patent")) {
            return "Patent Query";
        }
        if (lower.contains("trademark")) {
            return "Trademark Query";
        }
        if (lower.contains("copyright")) {
            return "Copyright Query";
        }
        if (lower.contains("geographical indication") || lower.matches("(?i).*\\bgi\\b.*")) {
            return "Geographical Indication";
        }
        String title = trimmed;
        String[] prefixes = {"what is a ", "what is an ", "what is ", "what are ", "explain ", "can i ", "how to "};
        for (String p : prefixes) {
            if (title.toLowerCase(java.util.Locale.ROOT).startsWith(p)) {
                title = title.substring(p.length()).trim();
                if (!title.isEmpty()) {
                    title = Character.toUpperCase(title.charAt(0)) + title.substring(1);
                }
                break;
            }
        }
        title = title.replaceAll("[?.!]+$", "").trim();
        if (title.length() > 40) {
            title = title.substring(0, 37).trim() + "...";
        }
        return title.isBlank() ? "New Conversation" : title;
    }

    @Transactional
    public ConversationMessageResponse persistAssistantResponse(
            UserPrincipal principal,
            UUID conversationId,
            UUID userMessageId,
            QuestionResponse response
    ) {
        ConversationEntity conversation = findAndVerifyOwnership(principal, conversationId);

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
                response.route(),
                response.domain(),
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

    // Legacy overload for tests/bypass - delegates to verified version when principal available
    @Transactional
    public ConversationMessageResponse persistAssistantResponse(UUID conversationId, UUID userMessageId, QuestionResponse response) {
        ConversationEntity conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ConversationNotFoundException("Conversation not found with id: " + conversationId));
        // Use same logic but without ownership check (internal use)
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
        List<QuestionCitation> citations = response.citations() != null ? response.citations() : List.of();
        int citationOrdinal = 0;
        for (QuestionCitation c : citations) {
            MessageCitationEntity citationEntity = new MessageCitationEntity(savedAssistantMessage, c.document(), c.documentId(), c.page(), c.section(), c.authority(), c.sourceUrl(), c.chunkId(), citationOrdinal++);
            savedAssistantMessage.addCitation(citationEntity);
            citationRepository.save(citationEntity);
        }
        List<QuestionSource> sources = response.sources() != null ? response.sources() : List.of();
        int sourceOrdinal = 0;
        for (QuestionSource s : sources) {
            MessageSourceEntity sourceEntity = new MessageSourceEntity(savedAssistantMessage, s.documentId(), s.score(), sourceOrdinal++);
            savedAssistantMessage.addSource(sourceEntity);
            sourceRepository.save(sourceEntity);
        }
        conversation.setUpdatedAt(Instant.now());
        conversationRepository.save(conversation);
        return new ConversationMessageResponse(conversationId, savedAssistantMessage.getId(), userMessageId, response.answer(), response.answerType() != null ? response.answerType().name() : null, response.route(), response.domain(), response.confidence(), response.abstained(), response.jurisdiction(), response.language(), response.detectedLanguage(), response.processingLanguage(), response.intent(), citations, sources, savedAssistantMessage.getCreatedAt());
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
