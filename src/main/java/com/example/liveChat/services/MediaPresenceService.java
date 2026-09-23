package com.example.liveChat.services;

import com.example.liveChat.dto.MediaPresenceEventDTO;
import com.example.liveChat.dto.MediaSessionResponseDTO;
import com.example.liveChat.dto.MediaStateRequestDTO;
import com.example.liveChat.exceptions.DirectChannelNotFoundException;
import com.example.liveChat.exceptions.InvalidRequestException;
import com.example.liveChat.exceptions.MediaInfrastructureUnavailableException;
import com.example.liveChat.exceptions.ResourceConflictException;
import com.example.liveChat.exceptions.ServerChannelNotFoundException;
import com.example.liveChat.exceptions.ServerNotFoundException;
import com.example.liveChat.models.ChannelType;
import com.example.liveChat.models.DirectChannel;
import com.example.liveChat.models.MediaChannelKind;
import com.example.liveChat.models.MediaSession;
import com.example.liveChat.models.MediaSessionStatus;
import com.example.liveChat.models.ServerChannel;
import com.example.liveChat.models.User;
import com.example.liveChat.repositories.DirectChannelRepository;
import com.example.liveChat.repositories.ServerChannelRepository;
import com.example.liveChat.repositories.ServerMemberRepository;
import com.example.liveChat.repositories.ServerRepository;
import com.example.liveChat.repositories.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.UUID;

@Service
public class MediaPresenceService {
    private static final Logger LOGGER = LoggerFactory.getLogger(MediaPresenceService.class);
    private static final String PRESENCE_DESTINATION = "/queue/media-presence";
    private static final String PARTICIPANT_JOINED = "media.participant.joined";
    private static final String PARTICIPANT_LEFT = "media.participant.left";
    private static final String PARTICIPANT_UPDATED = "media.participant.updated";

    private final MediaSessionStore mediaSessionStore;
    private final ServerRepository serverRepository;
    private final ServerMemberRepository serverMemberRepository;
    private final ServerChannelRepository serverChannelRepository;
    private final DirectChannelRepository directChannelRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final MediaPresenceProperties properties;
    private final LiveKitMediaService liveKitMediaService;

    public MediaPresenceService(MediaSessionStore mediaSessionStore, ServerRepository serverRepository,
                                ServerMemberRepository serverMemberRepository,
                                ServerChannelRepository serverChannelRepository,
                                DirectChannelRepository directChannelRepository, UserRepository userRepository,
                                SimpMessagingTemplate messagingTemplate, MediaPresenceProperties properties,
                                LiveKitMediaService liveKitMediaService) {
        this.mediaSessionStore = mediaSessionStore;
        this.serverRepository = serverRepository;
        this.serverMemberRepository = serverMemberRepository;
        this.serverChannelRepository = serverChannelRepository;
        this.directChannelRepository = directChannelRepository;
        this.userRepository = userRepository;
        this.messagingTemplate = messagingTemplate;
        this.properties = properties;
        this.liveKitMediaService = liveKitMediaService;
    }

    public MediaSessionResponseDTO joinServerVoiceChannel(String serverId, String channelId, User user) {
        serverVoiceChannel(serverId, channelId, user);
        var connection = liveKitMediaService.prepareConnection(MediaChannelKind.SERVER_VOICE, channelId, user);
        synchronized (this) {
            return join(MediaSession.active(MediaChannelKind.SERVER_VOICE, serverId, channelId, user))
                    .withConnection(connection);
        }
    }

    public synchronized List<MediaSessionResponseDTO> listServerVoiceChannelSessions(String serverId, String channelId,
                                                                                       User user) {
        serverVoiceChannel(serverId, channelId, user);
        return sessionsIn(MediaChannelKind.SERVER_VOICE, channelId);
    }

    public synchronized void leaveServerVoiceChannel(String serverId, String channelId, User user) {
        serverVoiceChannel(serverId, channelId, user);
        leave(MediaChannelKind.SERVER_VOICE, channelId, user);
    }

    public synchronized MediaSessionResponseDTO updateServerVoiceChannelState(String serverId, String channelId,
                                                                                 MediaStateRequestDTO request, User user) {
        serverVoiceChannel(serverId, channelId, user);
        return updateState(MediaChannelKind.SERVER_VOICE, channelId, request, user);
    }

    public MediaSessionResponseDTO joinDirectChannel(String channelId, User user) {
        directChannel(channelId, user);
        var connection = liveKitMediaService.prepareConnection(MediaChannelKind.DIRECT, channelId, user);
        synchronized (this) {
            return join(MediaSession.active(MediaChannelKind.DIRECT, null, channelId, user))
                    .withConnection(connection);
        }
    }

    public synchronized List<MediaSessionResponseDTO> listDirectChannelSessions(String channelId, User user) {
        directChannel(channelId, user);
        return sessionsIn(MediaChannelKind.DIRECT, channelId);
    }

    public synchronized void leaveDirectChannel(String channelId, User user) {
        directChannel(channelId, user);
        leave(MediaChannelKind.DIRECT, channelId, user);
    }

    public synchronized MediaSessionResponseDTO updateDirectChannelState(String channelId, MediaStateRequestDTO request,
                                                                            User user) {
        directChannel(channelId, user);
        return updateState(MediaChannelKind.DIRECT, channelId, request, user);
    }

    /** Chamada pelo evento de desconexão do STOMP. A expiração é processada pelo armazenamento de sessões. */
    public synchronized void markReconnectingAfterDisconnect(String userEmail) {
        userRepository.findActiveByEmailIgnoreCase(userEmail).ifPresent(user -> mediaSessionStore.findByUserId(user.getId())
                .filter(session -> session.status() == MediaSessionStatus.ACTIVE)
                .ifPresent(session -> {
                    MediaSession reconnecting = session.reconnecting(UUID.randomUUID().toString());
                    mediaSessionStore.markReconnecting(reconnecting);
                    publish(PARTICIPANT_UPDATED, reconnecting);
                }));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public synchronized void removeDeletedUserSession(AccountDeletedEvent event) {
        mediaSessionStore.findByUserId(event.userId()).ifPresent(session -> {
            try {
                mediaSessionStore.delete(session);
            } catch (RuntimeException exception) {
                LOGGER.warn("Could not remove media session for deleted user {}", event.userId(), exception);
            }
            try {
                liveKitMediaService.disconnect(session);
            } catch (RuntimeException exception) {
                LOGGER.warn("Could not disconnect deleted user {} from LiveKit", event.userId(), exception);
            }
            try {
                publish(PARTICIPANT_LEFT, session.anonymized());
            } catch (RuntimeException exception) {
                LOGGER.warn("Could not publish media departure for deleted user {}", event.userId(), exception);
            }
        });
    }

    @EventListener
    public synchronized void expireReconnectingSession(MediaSessionExpiryEvent event) {
        mediaSessionStore.removeExpired(event.userId(), event.reconnectionId())
                .ifPresent(session -> {
                    try {
                        liveKitMediaService.disconnect(session);
                    } catch (MediaInfrastructureUnavailableException exception) {
                        LOGGER.warn("Could not disconnect expired media session for user {}", session.userId(), exception);
                    }
                    publish(PARTICIPANT_LEFT, session);
                });
    }

    private MediaSessionResponseDTO join(MediaSession requestedSession) {
        MediaSession previous = mediaSessionStore.findByUserId(requestedSession.userId()).orElse(null);
        if (previous != null && previous.belongsTo(requestedSession.channelKind(), requestedSession.channelId())) {
            if (previous.status() == MediaSessionStatus.RECONNECTING) {
                MediaSession reactivated = previous.reactivate();
                mediaSessionStore.delete(previous);
                mediaSessionStore.saveActive(reactivated);
                publish(PARTICIPANT_UPDATED, reactivated);
                return MediaSessionResponseDTO.from(reactivated);
            }
            return MediaSessionResponseDTO.from(previous);
        }
        if (requestedSession.channelKind() == MediaChannelKind.SERVER_VOICE
                && mediaSessionStore.findByChannel(MediaChannelKind.SERVER_VOICE, requestedSession.channelId()).size()
                >= properties.getMaxServerParticipants()) {
            throw new ResourceConflictException("The voice channel participant limit has been reached");
        }
        if (previous != null) {
            liveKitMediaService.disconnect(previous);
            mediaSessionStore.delete(previous);
            publish(PARTICIPANT_LEFT, previous);
        }
        mediaSessionStore.saveActive(requestedSession);
        publish(PARTICIPANT_JOINED, requestedSession);
        return MediaSessionResponseDTO.from(requestedSession);
    }

    private List<MediaSessionResponseDTO> sessionsIn(MediaChannelKind channelKind, String channelId) {
        return mediaSessionStore.findByChannel(channelKind, channelId).stream()
                .map(MediaSessionResponseDTO::from)
                .toList();
    }

    private void leave(MediaChannelKind channelKind, String channelId, User user) {
        mediaSessionStore.findByUserId(user.getId())
                .filter(session -> session.belongsTo(channelKind, channelId))
                .ifPresent(session -> {
                    liveKitMediaService.disconnect(session);
                    mediaSessionStore.delete(session);
                    publish(PARTICIPANT_LEFT, session);
                });
    }

    private MediaSessionResponseDTO updateState(MediaChannelKind channelKind, String channelId,
                                                MediaStateRequestDTO request, User user) {
        if (request == null || request.isEmpty()) {
            throw new InvalidRequestException("At least one media state is required");
        }
        MediaSession session = mediaSessionStore.findByUserId(user.getId())
                .filter(current -> current.belongsTo(channelKind, channelId))
                .orElseThrow(() -> new ResourceConflictException("Join the media channel before changing its state"));
        if (session.status() != MediaSessionStatus.ACTIVE) {
            throw new ResourceConflictException("Reconnect to the media channel before changing its state");
        }
        MediaSession updated = session.withMediaState(request.microphoneEnabled(), request.cameraEnabled(),
                request.screenShareEnabled());
        mediaSessionStore.saveActive(updated);
        publish(PARTICIPANT_UPDATED, updated);
        return MediaSessionResponseDTO.from(updated);
    }

    private ServerChannel serverVoiceChannel(String serverId, String channelId, User user) {
        serverRepository.findById(serverId)
                .orElseThrow(() -> new ServerNotFoundException("Server not found"));
        if (serverMemberRepository.findByServerIdAndUserId(serverId, user.getId()).isEmpty()) {
            throw new AccessDeniedException("You are not a member of this server");
        }
        ServerChannel channel = serverChannelRepository.findById(channelId)
                .filter(candidate -> candidate.getServer().getId().equals(serverId))
                .orElseThrow(() -> new ServerChannelNotFoundException("Server channel not found"));
        if (channel.getType() != ChannelType.VOICE) {
            throw new InvalidRequestException("Media presence is only available in voice channels");
        }
        return channel;
    }

    private DirectChannel directChannel(String channelId, User user) {
        DirectChannel channel = directChannelRepository.findById(channelId)
                .orElseThrow(() -> new DirectChannelNotFoundException("Direct channel not found"));
        if (!channel.hasParticipant(user.getId())) {
            throw new AccessDeniedException("You are not a participant of this direct channel");
        }
        return channel;
    }

    private void publish(String type, MediaSession session) {
        MediaPresenceEventDTO event = new MediaPresenceEventDTO(type, MediaSessionResponseDTO.from(session));
        recipients(session).forEach(email -> messagingTemplate.convertAndSendToUser(email, PRESENCE_DESTINATION, event));
    }

    private List<String> recipients(MediaSession session) {
        if (session.channelKind() == MediaChannelKind.SERVER_VOICE) {
            return serverMemberRepository.findByServerId(session.serverId()).stream()
                    .map(member -> member.getUser())
                    .filter(User::isEnabled)
                    .map(User::getEmail)
                    .distinct()
                    .toList();
        }
        return directChannelRepository.findById(session.channelId())
                .map(channel -> List.of(channel.getParticipantOne(), channel.getParticipantTwo()).stream()
                        .filter(User::isEnabled)
                        .map(User::getEmail)
                        .toList())
                .orElseGet(List::of);
    }
}
