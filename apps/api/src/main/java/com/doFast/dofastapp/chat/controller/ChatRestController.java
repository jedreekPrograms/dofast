package com.doFast.dofastapp.chat.controller;

import com.doFast.dofastapp.chat.dto.ChatConversationResponse;
import com.doFast.dofastapp.chat.dto.ChatHistoryResponse;
import com.doFast.dofastapp.chat.dto.ChatMessageResponse;
import com.doFast.dofastapp.chat.dto.MarkChatReadRequest;
import com.doFast.dofastapp.chat.dto.SendChatMessageRequest;
import com.doFast.dofastapp.chat.service.ChatService;
import com.doFast.dofastapp.user.entity.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/chat")
@Validated
public class ChatRestController {

    private final ChatService chatService;

    public ChatRestController(ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping("/conversations")
    public List<ChatConversationResponse> conversations(@AuthenticationPrincipal User user) {
        return chatService.getConversations(user);
    }

    @GetMapping("/jobs/{jobId}/messages")
    public ChatHistoryResponse history(
            @PathVariable Long jobId,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit,
            @AuthenticationPrincipal User user
    ) {
        return chatService.getHistory(jobId, user, beforeId, limit);
    }

    @PostMapping("/jobs/{jobId}/messages")
    public ResponseEntity<ChatMessageResponse> send(
            @PathVariable Long jobId,
            @Valid @RequestBody SendChatMessageRequest request,
            @AuthenticationPrincipal User user
    ) {
        ChatMessageResponse response = chatService.sendMessage(
                jobId,
                user,
                request.content(),
                request.clientMessageId()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/jobs/{jobId}/read")
    public ResponseEntity<Void> markRead(
            @PathVariable Long jobId,
            @Valid @RequestBody MarkChatReadRequest request,
            @AuthenticationPrincipal User user
    ) {
        chatService.markRead(jobId, request.lastMessageId(), user);
        return ResponseEntity.noContent().build();
    }
}
