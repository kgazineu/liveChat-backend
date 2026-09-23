package com.example.liveChat.services;

import com.example.liveChat.dto.ChannelResponseDTO;
import com.example.liveChat.dto.CreateChannelRequestDTO;
import com.example.liveChat.dto.CreateServerInviteRequestDTO;
import com.example.liveChat.dto.CreateServerRequestDTO;
import com.example.liveChat.dto.ServerInviteEventDTO;
import com.example.liveChat.dto.ServerInviteResponseDTO;
import com.example.liveChat.dto.ServerMemberEventDTO;
import com.example.liveChat.dto.ServerMemberResponseDTO;
import com.example.liveChat.dto.ServerResponseDTO;
import com.example.liveChat.exceptions.InvalidRequestException;
import com.example.liveChat.exceptions.ResourceConflictException;
import com.example.liveChat.exceptions.ServerInviteNotFoundException;
import com.example.liveChat.exceptions.ServerNotFoundException;
import com.example.liveChat.models.Server;
import com.example.liveChat.models.ServerChannel;
import com.example.liveChat.models.ServerInvite;
import com.example.liveChat.models.ServerInviteStatus;
import com.example.liveChat.models.ServerMember;
import com.example.liveChat.models.ServerRole;
import com.example.liveChat.models.User;
import com.example.liveChat.repositories.FriendshipRepository;
import com.example.liveChat.repositories.ServerChannelRepository;
import com.example.liveChat.repositories.ServerInviteRepository;
import com.example.liveChat.repositories.ServerMemberRepository;
import com.example.liveChat.repositories.ServerRepository;
import com.example.liveChat.repositories.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ServerService {
    private static final int MAX_NAME_LENGTH = 100;

    private final ServerRepository serverRepository;
    private final ServerMemberRepository serverMemberRepository;
    private final ServerChannelRepository serverChannelRepository;
    private final ServerInviteRepository serverInviteRepository;
    private final FriendshipRepository friendshipRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    public ServerService(ServerRepository serverRepository, ServerMemberRepository serverMemberRepository,
                         ServerChannelRepository serverChannelRepository, ServerInviteRepository serverInviteRepository,
                         FriendshipRepository friendshipRepository, UserRepository userRepository,
                         ApplicationEventPublisher eventPublisher) {
        this.serverRepository = serverRepository;
        this.serverMemberRepository = serverMemberRepository;
        this.serverChannelRepository = serverChannelRepository;
        this.serverInviteRepository = serverInviteRepository;
        this.friendshipRepository = friendshipRepository;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public ServerResponseDTO create(CreateServerRequestDTO request, User owner) {
        requireActive(owner);
        String name = requiredName(request == null ? null : request.name(), "Server name");
        Server server = serverRepository.save(new Server(name, owner));
        ServerMember member = serverMemberRepository.save(new ServerMember(server, owner, ServerRole.OWNER));
        return ServerResponseDTO.from(server, member);
    }

    @Transactional(readOnly = true)
    public List<ServerResponseDTO> listFor(User user) {
        requireActive(user);
        return serverMemberRepository.findByUserIdOrderByJoinedAtDesc(user.getId()).stream()
                .map(member -> ServerResponseDTO.from(member.getServer(), member))
                .toList();
    }

    @Transactional(readOnly = true)
    public ServerResponseDTO get(String serverId, User user) {
        requireActive(user);
        Server server = getServer(serverId);
        return ServerResponseDTO.from(server, getMember(serverId, user));
    }

    @Transactional(readOnly = true)
    public List<ServerMemberResponseDTO> listMembers(String serverId, User user) {
        requireActive(user);
        getServer(serverId);
        getMember(serverId, user);
        return serverMemberRepository.findByServerIdOrderByJoinedAtAscIdAsc(serverId).stream()
                .map(ServerMemberResponseDTO::from)
                .toList();
    }

    @Transactional
    public ChannelResponseDTO createChannel(String serverId, CreateChannelRequestDTO request, User user) {
        requireActive(user);
        Server server = getServer(serverId);
        ServerMember member = getMember(serverId, user);
        if (member.getRole() != ServerRole.OWNER) {
            throw new AccessDeniedException("Only the server owner can create channels");
        }
        if (request == null || request.type() == null) {
            throw new InvalidRequestException("Channel type is required");
        }
        String name = requiredName(request.name(), "Channel name");
        int position = Math.toIntExact(serverChannelRepository.countByServerId(serverId));
        ServerChannel channel = serverChannelRepository.save(new ServerChannel(server, name, request.type(), position));
        return ChannelResponseDTO.from(channel);
    }

    @Transactional(readOnly = true)
    public List<ChannelResponseDTO> listChannels(String serverId, User user) {
        requireActive(user);
        getServer(serverId);
        getMember(serverId, user);
        return serverChannelRepository.findByServerIdOrderByPositionAscIdAsc(serverId).stream()
                .map(ChannelResponseDTO::from)
                .toList();
    }

    @Transactional
    public ServerInviteResponseDTO inviteFriend(String serverId, CreateServerInviteRequestDTO request, User inviter) {
        requireActive(inviter);
        Server server = getServer(serverId);
        getMember(serverId, inviter);
        if (request == null || request.friendId() == null || request.friendId().isBlank()) {
            throw new InvalidRequestException("Friend id is required");
        }
        if (inviter.getId().equals(request.friendId())) {
            throw new InvalidRequestException("You cannot invite yourself");
        }

        User invitee = userRepository.findActiveById(request.friendId())
                .orElseThrow(() -> new InvalidRequestException("Friend not found"));
        if (!friendshipRepository.areFriends(inviter, invitee)) {
            throw new AccessDeniedException("You can only invite accepted friends");
        }
        if (serverMemberRepository.findByServerIdAndUserId(serverId, invitee.getId()).isPresent()) {
            throw new ResourceConflictException("User is already a member of this server");
        }
        if (serverInviteRepository.existsByServerIdAndInviteeId(serverId, invitee.getId())) {
            throw new ResourceConflictException("A server invite already exists for this user");
        }

        ServerInvite invite = serverInviteRepository.save(new ServerInvite(server, inviter, invitee));
        eventPublisher.publishEvent(SocialNotificationEvent.serverInvites(List.of(invitee.getEmail()),
                ServerInviteEventDTO.from("server.invite.created", invite)));
        return ServerInviteResponseDTO.from(invite);
    }

    @Transactional(readOnly = true)
    public List<ServerInviteResponseDTO> listPendingInvites(User user) {
        requireActive(user);
        return serverInviteRepository.findByInviteeIdAndStatusOrderByCreatedAtDesc(user.getId(), ServerInviteStatus.PENDING)
                .stream()
                .map(ServerInviteResponseDTO::from)
                .toList();
    }

    @Transactional
    public ServerResponseDTO acceptInvite(Long inviteId, User user) {
        requireActive(user);
        ServerInvite invite = serverInviteRepository.findByIdAndInviteeId(inviteId, user.getId())
                .orElseThrow(() -> new ServerInviteNotFoundException("Server invite not found"));
        if (invite.getStatus() != ServerInviteStatus.PENDING) {
            throw new ResourceConflictException("Server invite has already been accepted");
        }
        String serverId = invite.getServer().getId();
        if (serverMemberRepository.findByServerIdAndUserId(serverId, user.getId()).isPresent()) {
            throw new ResourceConflictException("User is already a member of this server");
        }

        ServerMember member = serverMemberRepository.save(new ServerMember(invite.getServer(), user, ServerRole.MEMBER));
        invite.accept();

        eventPublisher.publishEvent(SocialNotificationEvent.serverInvites(
                List.of(invite.getInvitee().getEmail(), invite.getInviter().getEmail()),
                ServerInviteEventDTO.from("server.invite.accepted", invite)));
        List<String> memberEmails = serverMemberRepository.findByServerIdOrderByJoinedAtAscIdAsc(serverId).stream()
                .map(serverMember -> serverMember.getUser().getEmail())
                .toList();
        eventPublisher.publishEvent(SocialNotificationEvent.serverMembers(memberEmails,
                new ServerMemberEventDTO("server.member.joined", serverId, ServerMemberResponseDTO.from(member))));
        return ServerResponseDTO.from(invite.getServer(), member);
    }

    private void requireActive(User user) {
        if (user == null || user.getId() == null || userRepository.findActiveById(user.getId()).isEmpty()) {
            throw new AccessDeniedException("Active user required");
        }
    }

    private Server getServer(String serverId) {
        return serverRepository.findById(serverId)
                .orElseThrow(() -> new ServerNotFoundException("Server not found"));
    }

    private ServerMember getMember(String serverId, User user) {
        return serverMemberRepository.findByServerIdAndUserId(serverId, user.getId())
                .orElseThrow(() -> new AccessDeniedException("You are not a member of this server"));
    }

    private String requiredName(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new InvalidRequestException(fieldName + " is required");
        }
        String normalized = value.trim();
        if (normalized.length() > MAX_NAME_LENGTH) {
            throw new InvalidRequestException(fieldName + " must have at most " + MAX_NAME_LENGTH + " characters");
        }
        return normalized;
    }
}
