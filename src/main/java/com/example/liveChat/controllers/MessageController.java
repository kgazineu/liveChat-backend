package com.example.liveChat.controllers;

import com.example.liveChat.dto.MessageRequestDTO;
import com.example.liveChat.dto.MessageResponseDTO;
import com.example.liveChat.services.MessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

import java.security.Principal;
import java.util.List;

@Controller
public class MessageController {

    @Autowired
    private MessageService messageService;

    @MessageMapping("/chat")
    public void processMessage(@Payload MessageRequestDTO messageRequest, Principal principal) {
        messageService.sendMessage(principal.getName(), messageRequest);
    }

    @GetMapping("/messages/{targetUserId}")
    @ResponseBody
    public ResponseEntity<List<MessageResponseDTO>> getChatHistory(@PathVariable String targetUserId, Principal principal) {
        List<MessageResponseDTO> history = messageService.getChatHistory(principal.getName(), targetUserId);
        return ResponseEntity.ok(history);
    }
}