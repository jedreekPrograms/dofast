package com.doFast.dofastapp.chat.controller;

import com.doFast.dofastapp.chat.dto.ChatMessageRequest;
import com.doFast.dofastapp.chat.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @MessageMapping("/chat/send")
    public void send(@Valid @Payload ChatMessageRequest request, Principal principal) {
        if (principal == null) {
            throw new IllegalStateException("WebSocket session is not authenticated");
        }

        chatService.sendMessageByEmail(
                request.getJobId(),
                principal.getName(),
                request.getContent(),
                request.getClientMessageId()
        );
    }
}
