package com.example.liveChat.services;

import com.example.liveChat.dto.CreateDirectChannelRequestDTO;
import com.example.liveChat.dto.DirectChannelResponseDTO;
import com.example.liveChat.exceptions.DirectChannelNotFoundException;
import com.example.liveChat.exceptions.InvalidRequestException;
import com.example.liveChat.exceptions.UserNotFoundException;
import com.example.liveChat.models.DirectChannel;
import com.example.liveChat.models.User;
import com.example.liveChat.repositories.DirectChannelRepository;
import com.example.liveChat.repositories.FriendshipRepository;
import com.example.liveChat.repositories.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DirectChannelService {
    private final DirectChannelRepository directChannelRepository;
    private final UserRepository userRepository;
    private final FriendshipRepository friendshipRepository;

    public DirectChannelService(DirectChannelRepository directChannelRepository, UserRepository userRepository,
                                FriendshipRepository friendshipRepository) {
        this.directChannelRepository = directChannelRepository;
        this.userRepository = userRepository;
        this.friendshipRepository = friendshipRepository;
    }

    @Transactional
    public CreateOrGetResult createOrGet(CreateDirectChannelRequestDTO request, User currentUser) {
        String participantId = request == null ? null : request.participantId();
        if (participantId == null || participantId.isBlank()) {
            throw new InvalidRequestException("Participant id is required");
        }
        if (currentUser.getId().equals(participantId)) {
            throw new InvalidRequestException("You cannot create a direct channel with yourself");
        }

        User activeCurrentUser = userRepository.findActiveById(currentUser.getId())
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        User otherUser = userRepository.findActiveById(participantId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        boolean currentUserComesFirst = activeCurrentUser.getId().compareTo(otherUser.getId()) < 0;
        User participantOne = currentUserComesFirst ? activeCurrentUser : otherUser;
        User participantTwo = currentUserComesFirst ? otherUser : activeCurrentUser;

        return directChannelRepository.findByParticipantOneIdAndParticipantTwoId(
                        participantOne.getId(), participantTwo.getId())
                .map(channel -> new CreateOrGetResult(
                        DirectChannelResponseDTO.from(channel, activeCurrentUser.getId()), false))
                .orElseGet(() -> {
                    if (!friendshipRepository.areFriends(activeCurrentUser, otherUser)) {
                        throw new AccessDeniedException("You can only create a direct channel with an accepted friend");
                    }
                    DirectChannel created = directChannelRepository.save(new DirectChannel(participantOne, participantTwo));
                    return new CreateOrGetResult(DirectChannelResponseDTO.from(created, activeCurrentUser.getId()), true);
                });
    }

    @Transactional(readOnly = true)
    public List<DirectChannelResponseDTO> list(User currentUser) {
        requireActive(currentUser);
        return directChannelRepository.findByParticipantOneIdOrParticipantTwoIdOrderByCreatedAtDesc(
                        currentUser.getId(), currentUser.getId()).stream()
                .map(channel -> DirectChannelResponseDTO.from(channel, currentUser.getId()))
                .toList();
    }

    @Transactional(readOnly = true)
    public DirectChannelResponseDTO get(String channelId, User currentUser) {
        requireActive(currentUser);
        DirectChannel channel = directChannelRepository.findById(channelId)
                .orElseThrow(() -> new DirectChannelNotFoundException("Direct channel not found"));
        if (!channel.hasParticipant(currentUser.getId())) {
            throw new AccessDeniedException("You are not a participant of this direct channel");
        }
        return DirectChannelResponseDTO.from(channel, currentUser.getId());
    }

    private void requireActive(User user) {
        if (user == null || user.getId() == null || userRepository.findActiveById(user.getId()).isEmpty()) {
            throw new UserNotFoundException("User not found");
        }
    }

    public record CreateOrGetResult(DirectChannelResponseDTO channel, boolean created) {
    }
}
