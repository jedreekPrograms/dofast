package com.doFast.dofastapp.chat.controller;

import com.doFast.dofastapp.chat.dto.ChatMessageRequest;
import com.doFast.dofastapp.chat.dto.ChatMessageResponse;
import com.doFast.dofastapp.chat.service.ChatService;
import com.doFast.dofastapp.user.entity.User;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;

@Controller
public class ChatController {

    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;

    public ChatController(ChatService chatService, SimpMessagingTemplate messagingTemplate) {
        this.chatService = chatService;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/chat/send")
    public void send(ChatMessageRequest request) {

        ChatMessageResponse response =
                chatService.sendMessage(
                        request.getJobId(),
                        request.getSenderId(),
                        request.getContent()

                );

        messagingTemplate.convertAndSend(
                "/topic/chat/" + request.getJobId(),
                response
        );
    }
}
