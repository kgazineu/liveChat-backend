package com.example.liveChat.services;

import com.example.liveChat.dto.ChannelResponseDTO;
import com.example.liveChat.dto.CreateChannelRequestDTO;
import com.example.liveChat.dto.CreateServerRequestDTO;
import com.example.liveChat.dto.ServerResponseDTO;
import com.example.liveChat.exceptions.InvalidRequestException;
import com.example.liveChat.exceptions.ServerNotFoundException;
import com.example.liveChat.models.Server;
import com.example.liveChat.models.ServerChannel;
import com.example.liveChat.models.ServerMember;
import com.example.liveChat.models.ServerRole;
import com.example.liveChat.models.User;
import com.example.liveChat.repositories.ServerChannelRepository;
import com.example.liveChat.repositories.ServerMemberRepository;
import com.example.liveChat.repositories.ServerRepository;
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

    public ServerService(ServerRepository serverRepository, ServerMemberRepository serverMemberRepository,
                         ServerChannelRepository serverChannelRepository) {
        this.serverRepository = serverRepository;
        this.serverMemberRepository = serverMemberRepository;
        this.serverChannelRepository = serverChannelRepository;
    }

    @Transactional
    public ServerResponseDTO create(CreateServerRequestDTO request, User owner) {
        String name = requiredName(request == null ? null : request.name(), "Server name");
        Server server = serverRepository.save(new Server(name, owner));
        ServerMember member = serverMemberRepository.save(new ServerMember(server, owner, ServerRole.OWNER));
        return ServerResponseDTO.from(server, member);
    }

    @Transactional(readOnly = true)
    public List<ServerResponseDTO> listFor(User user) {
        return serverMemberRepository.findByUserIdOrderByJoinedAtDesc(user.getId()).stream()
                .map(member -> ServerResponseDTO.from(member.getServer(), member))
                .toList();
    }

    @Transactional(readOnly = true)
    public ServerResponseDTO get(String serverId, User user) {
        Server server = getServer(serverId);
        return ServerResponseDTO.from(server, getMember(serverId, user));
    }

    @Transactional
    public ChannelResponseDTO createChannel(String serverId, CreateChannelRequestDTO request, User user) {
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
        getServer(serverId);
        getMember(serverId, user);
        return serverChannelRepository.findByServerIdOrderByPositionAscIdAsc(serverId).stream()
                .map(ChannelResponseDTO::from)
                .toList();
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
