package com.example.liveChat.services;

import com.example.liveChat.dto.MessageRequestDTO;
import com.example.liveChat.dto.MessageResponseDTO;
import com.example.liveChat.models.Message;
import com.example.liveChat.models.User;
import com.example.liveChat.repositories.MessageRepository;
import com.example.liveChat.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class MessageService {

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    public MessageResponseDTO sendMessage(String senderEmail, MessageRequestDTO request) {
        User sender = userRepository.findByEmail(senderEmail)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));

        User receiver = userRepository.findById(String.valueOf(request.receiverId()))
                .orElseThrow(() -> new RuntimeException("Destinatário não encontrado"));

        Message message = new Message(sender, receiver, request.content());
        Message savedMessage = messageRepository.save(message);

        MessageResponseDTO response = new MessageResponseDTO(
                savedMessage.getId(),
                savedMessage.getContent(),
                sender.getId(),
                sender.getName(),
                sender.getEmail(),
                receiver.getId(),
                receiver.getEmail(),
                savedMessage.getTimestamp()
        );

        messagingTemplate.convertAndSendToUser(
                String.valueOf(receiver.getEmail()),
                "/queue/messages",
                response
        );

        messagingTemplate.convertAndSendToUser(
                String.valueOf(sender.getEmail()),
                "/queue/messages",
                response
        );

        return response;
    }

    public List<MessageResponseDTO> getChatHistory(String currentUserEmail, String otherUserId) {
        User currentUser = userRepository.findByEmail(currentUserEmail)
                .orElseThrow(() -> new RuntimeException("Usuário atual não encontrado"));

        User otherUser = userRepository.findById(otherUserId)
                .orElseThrow(() -> new RuntimeException("Outro usuário não encontrado"));

        List<Message> messages = messageRepository.findChatHistory(currentUser, otherUser);

        return messages.stream()
                .map(m -> new MessageResponseDTO(
                        m.getId(),
                        m.getContent(),
                        m.getSender().getId(),
                        m.getSender().getName(),
                        m.getSender().getEmail(),
                        m.getReceiver().getId(),
                        m.getReceiver().getEmail(),
                        m.getTimestamp()))
                .collect(Collectors.toList());
    }
}