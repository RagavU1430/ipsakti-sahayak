package com.ipsakti.ip_sakti_backend.api;

import com.ipsakti.ip_sakti_backend.auth.UserPrincipal;
import com.ipsakti.ip_sakti_backend.conversation.ConversationService;
import com.ipsakti.ip_sakti_backend.conversation.dto.ConversationDetailResponse;
import com.ipsakti.ip_sakti_backend.conversation.dto.ConversationMessageRequest;
import com.ipsakti.ip_sakti_backend.conversation.dto.ConversationMessageResponse;
import com.ipsakti.ip_sakti_backend.conversation.dto.ConversationPageResponse;
import com.ipsakti.ip_sakti_backend.conversation.dto.ConversationSummaryResponse;
import com.ipsakti.ip_sakti_backend.conversation.dto.CreateConversationRequest;
import com.ipsakti.ip_sakti_backend.conversation.dto.UpdateConversationRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/conversations")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @PostMapping
    public ResponseEntity<ConversationSummaryResponse> createConversation(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody(required = false) CreateConversationRequest request
    ) {
        ensureAuthenticated(principal);
        ConversationSummaryResponse response = conversationService.createConversation(principal, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<ConversationPageResponse> listConversations(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        ensureAuthenticated(principal);
        return ResponseEntity.ok(conversationService.listConversations(principal, page, size));
    }

    @GetMapping("/{conversationId}")
    public ResponseEntity<ConversationDetailResponse> getConversation(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID conversationId
    ) {
        ensureAuthenticated(principal);
        return ResponseEntity.ok(conversationService.getConversation(principal, conversationId));
    }

    @PatchMapping("/{conversationId}")
    public ResponseEntity<ConversationSummaryResponse> updateConversation(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID conversationId,
            @Valid @RequestBody UpdateConversationRequest request
    ) {
        ensureAuthenticated(principal);
        return ResponseEntity.ok(conversationService.updateConversation(principal, conversationId, request));
    }

    @DeleteMapping("/{conversationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> deleteConversation(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID conversationId
    ) {
        ensureAuthenticated(principal);
        conversationService.deleteConversation(principal, conversationId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{conversationId}/messages")
    public ResponseEntity<ConversationMessageResponse> askInConversation(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID conversationId,
            @Valid @RequestBody ConversationMessageRequest request
    ) {
        ensureAuthenticated(principal);
        return ResponseEntity.ok(conversationService.askInConversation(principal, conversationId, request));
    }

    private void ensureAuthenticated(UserPrincipal principal) {
        if (principal == null) {
            throw new AccessDeniedException("User is not authenticated");
        }
    }
}
