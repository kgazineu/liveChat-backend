package com.example.liveChat.services;

import com.example.liveChat.dto.MessageRequestDTO;
import com.example.liveChat.dto.MessageResponseDTO;
import com.example.liveChat.dto.PageResponseDTO;
import com.example.liveChat.dto.PaginationRequestDTO;
import com.example.liveChat.exceptions.DirectChannelNotFoundException;
import com.example.liveChat.exceptions.InvalidRequestException;
import com.example.liveChat.exceptions.ServerChannelNotFoundException;
import com.example.liveChat.exceptions.UserNotFoundException;
import com.example.liveChat.infra.ratelimit.RateLimiter;
import com.example.liveChat.models.ChannelMessage;
import com.example.liveChat.models.ChannelType;
import com.example.liveChat.models.DirectChannel;
import com.example.liveChat.models.DirectMessage;
import com.example.liveChat.models.ServerChannel;
import com.example.liveChat.models.User;
import com.example.liveChat.repositories.ChannelMessageRepository;
import com.example.liveChat.repositories.DirectChannelRepository;
import com.example.liveChat.repositories.DirectMessageRepository;
import com.example.liveChat.repositories.ServerChannelRepository;
import com.example.liveChat.repositories.ServerMemberRepository;
import com.example.liveChat.repositories.UserRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;

@Service
public class MessageService {
    private static final int MAX_CONTENT_LENGTH = 4_000;
    private static final Duration MESSAGE_RATE_WINDOW = Duration.ofMinutes(1);
    private static final Duration MESSAGE_BURST_WINDOW = Duration.ofSeconds(5);

    private final DirectChannelRepository directChannelRepository;
    private final DirectMessageRepository directMessageRepository;
    private final ServerChannelRepository serverChannelRepository;
    private final ChannelMessageRepository channelMessageRepository;
    private final ServerMemberRepository serverMemberRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final RateLimiter rateLimiter;
    private final MessageAttachmentService attachmentService;

    public MessageService(DirectChannelRepository directChannelRepository,
                          DirectMessageRepository directMessageRepository,
                          ServerChannelRepository serverChannelRepository,
                          ChannelMessageRepository channelMessageRepository,
                          ServerMemberRepository serverMemberRepository,
                          UserRepository userRepository,
                          SimpMessagingTemplate messagingTemplate,
                          RateLimiter rateLimiter,
                          MessageAttachmentService attachmentService) {
        this.directChannelRepository = directChannelRepository;
        this.directMessageRepository = directMessageRepository;
        this.serverChannelRepository = serverChannelRepository;
        this.channelMessageRepository = channelMessageRepository;
        this.serverMemberRepository = serverMemberRepository;
        this.userRepository = userRepository;
        this.messagingTemplate = messagingTemplate;
        this.rateLimiter = rateLimiter;
        this.attachmentService = attachmentService;
    }

    @Transactional
    public MessageResponseDTO sendDirectMessage(String senderEmail, String channelId, MessageRequestDTO request) {
        User sender = getUser(senderEmail);
        DirectChannel directChannel = getDirectChannelForParticipant(channelId, sender);
        checkMessageRateLimits(sender.getId());
        var attachments = attachmentService.prepareForDirectMessage(attachmentIdsOf(request), sender, directChannel);
        DirectMessage message = directMessageRepository.save(
                new DirectMessage(directChannel, sender, contentOf(request, !attachments.isEmpty())));
        var attachmentResponses = attachmentService.attachToDirectMessage(attachments, message);
        MessageResponseDTO response = MessageResponseDTO.from(message, attachmentResponses);
        notifyUser(directChannel.getParticipantOne(), response);
        notifyUser(directChannel.getParticipantTwo(), response);
        return response;
    }

    @Transactional(readOnly = true)
    public PageResponseDTO<MessageResponseDTO> listDirectMessages(String userEmail, String channelId,
                                                                   PaginationRequestDTO pagination) {
        User user = getUser(userEmail);
        getDirectChannelForParticipant(channelId, user);
        var messages = directMessageRepository
                .findByDirectChannelIdOrderByCreatedAtDescIdDesc(channelId, pagination.toPageable());
        var attachments = attachmentService.responsesForDirectMessages(messages.getContent());
        return PageResponseDTO.from(messages.map(message -> MessageResponseDTO.from(
                message, attachments.getOrDefault(message.getId(), List.of()))));
    }

    @Transactional
    public MessageResponseDTO sendChannelMessage(String senderEmail, String serverId, String channelId,
                                                  MessageRequestDTO request) {
        User sender = getUser(senderEmail);
        ServerChannel textChannel = getTextChannelForMember(serverId, channelId, sender);
        checkMessageRateLimits(sender.getId());
        var attachments = attachmentService.prepareForChannelMessage(attachmentIdsOf(request), sender, textChannel);
        ChannelMessage message = channelMessageRepository.save(
                new ChannelMessage(textChannel, sender, contentOf(request, !attachments.isEmpty())));
        var attachmentResponses = attachmentService.attachToChannelMessage(attachments, message);
        MessageResponseDTO response = MessageResponseDTO.from(message, attachmentResponses);
        serverMemberRepository.findByServerId(serverId)
                .forEach(member -> notifyUser(member.getUser(), response));
        return response;
    }

    @Transactional(readOnly = true)
    public PageResponseDTO<MessageResponseDTO> listChannelMessages(String userEmail, String serverId, String channelId,
                                                                    PaginationRequestDTO pagination) {
        User user = getUser(userEmail);
        getTextChannelForMember(serverId, channelId, user);
        var messages = channelMessageRepository
                .findByChannelIdOrderByCreatedAtDescIdDesc(channelId, pagination.toPageable());
        var attachments = attachmentService.responsesForChannelMessages(messages.getContent());
        return PageResponseDTO.from(messages.map(message -> MessageResponseDTO.from(
                message, attachments.getOrDefault(message.getId(), List.of()))));
    }

    private User getUser(String email) {
        return userRepository.findActiveByEmailIgnoreCase(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
    }

    private DirectChannel getDirectChannelForParticipant(String channelId, User user) {
        DirectChannel directChannel = directChannelRepository.findById(channelId)
                .orElseThrow(() -> new DirectChannelNotFoundException("Direct channel not found"));
        if (!directChannel.hasParticipant(user.getId())) {
            throw new AccessDeniedException("You are not a participant of this direct channel");
        }
        return directChannel;
    }

    private ServerChannel getTextChannelForMember(String serverId, String channelId, User user) {
        ServerChannel channel = serverChannelRepository.findById(channelId)
                .orElseThrow(() -> new ServerChannelNotFoundException("Server channel not found"));
        if (!channel.getServer().getId().equals(serverId)) {
            throw new ServerChannelNotFoundException("Server channel not found");
        }
        if (channel.getType() != ChannelType.TEXT) {
            throw new InvalidRequestException("Messages can only be sent to text channels");
        }
        if (serverMemberRepository.findByServerIdAndUserId(serverId, user.getId()).isEmpty()) {
            throw new AccessDeniedException("You are not a member of this server");
        }
        return channel;
    }

    private void checkMessageRateLimits(String userId) {
        rateLimiter.check("message-user", userId, 60, MESSAGE_RATE_WINDOW);
        rateLimiter.check("message-burst-user", userId, 10, MESSAGE_BURST_WINDOW);
    }

    private List<String> attachmentIdsOf(MessageRequestDTO request) {
        return request == null || request.attachmentIds() == null ? List.of() : request.attachmentIds();
    }

    private String contentOf(MessageRequestDTO request, boolean hasAttachments) {
        String content = request == null ? null : request.content();
        if (content == null || content.isBlank()) {
            if (hasAttachments) return "";
            throw new InvalidRequestException("Message content or at least one attachment is required");
        }
        String normalized = content.trim();
        if (normalized.length() > MAX_CONTENT_LENGTH) {
            throw new InvalidRequestException("Message content must have at most " + MAX_CONTENT_LENGTH + " characters");
        }
        return normalized;
    }

    private void notifyUser(User user, MessageResponseDTO response) {
        messagingTemplate.convertAndSendToUser(user.getEmail(), "/queue/messages", response);
    }
}
