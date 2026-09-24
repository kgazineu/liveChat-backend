package com.example.liveChat.services;

import com.example.liveChat.dto.FriendshipEventDTO;
import com.example.liveChat.dto.FriendshipResponseDTO;
import com.example.liveChat.dto.PageResponseDTO;
import com.example.liveChat.dto.PaginationRequestDTO;
import com.example.liveChat.dto.UserResponseDTO;
import com.example.liveChat.exceptions.InvalidRequestException;
import com.example.liveChat.exceptions.RateLimitExceededException;
import com.example.liveChat.exceptions.ResourceConflictException;
import com.example.liveChat.exceptions.UserNotFoundException;
import com.example.liveChat.infra.ratelimit.RateLimiter;
import com.example.liveChat.models.Friendship;
import com.example.liveChat.models.FriendshipStatus;
import com.example.liveChat.models.User;
import com.example.liveChat.repositories.FriendshipRepository;
import com.example.liveChat.repositories.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class FriendshipService {
    private static final Duration REJECTED_REQUEST_COOLDOWN = Duration.ofHours(24);
    private static final Duration SENDER_RATE_LIMIT_WINDOW = Duration.ofHours(1);
    private static final Duration RECIPIENT_RATE_LIMIT_WINDOW = Duration.ofDays(1);

    private final FriendshipRepository friendshipRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final RateLimiter rateLimiter;

    public FriendshipService(FriendshipRepository friendshipRepository, UserRepository userRepository,
                             ApplicationEventPublisher eventPublisher, RateLimiter rateLimiter) {
        this.friendshipRepository = friendshipRepository;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
        this.rateLimiter = rateLimiter;
    }

    @Transactional
    public void sendFriendRequest(String requesterId, String addresseeId) {
        if (addresseeId == null || addresseeId.isBlank()) {
            throw new InvalidRequestException("Target user id is required");
        }
        if (requesterId.equals(addresseeId)) {
            throw new InvalidRequestException("User cannot send a friend request to themselves");
        }

        rateLimiter.check("friend-request-sender", requesterId, 10, SENDER_RATE_LIMIT_WINDOW);
        rateLimiter.check("friend-request-recipient", addresseeId, 20, RECIPIENT_RATE_LIMIT_WINDOW);

        User requester = userRepository.findActiveById(requesterId)
                .orElseThrow(() -> new UserNotFoundException("Requester not found"));
        User addressee = userRepository.findActiveById(addresseeId)
                .orElseThrow(() -> new UserNotFoundException("Target user not found"));

        Optional<Friendship> existingRelationship = friendshipRepository.findRelationship(requester, addressee);

        if (existingRelationship.isPresent()) {
            Friendship friendship = existingRelationship.get();

            if (friendship.getStatus() == FriendshipStatus.ACCEPTED || friendship.getStatus() == FriendshipStatus.PENDING) {
                throw new ResourceConflictException("Friend request cannot be sent to an existing relationship");
            }

            if (friendship.getStatus() == FriendshipStatus.REJECTED) {
                LocalDateTime now = LocalDateTime.now();
                enforceRejectedRequestCooldown(friendship, now);
                friendship.setStatus(FriendshipStatus.PENDING);
                friendship.setCreatedAt(now);

                friendship.setRequester(requester);
                friendship.setAddressee(addressee);

                Friendship savedFriendship = friendshipRepository.save(friendship);
                publish("friendship.request.created", savedFriendship, List.of(addressee.getEmail()));
                return;
            }
        }

        Friendship newFriendship = friendshipRepository.save(new Friendship(requester, addressee));
        publish("friendship.request.created", newFriendship, List.of(addressee.getEmail()));
    }

    @Transactional
    public void rejectFriendRequest(Long friendshipId, String userIdDoLogado) {
        requireActiveUser(userIdDoLogado);
        Friendship friendship = getFriendship(friendshipId);

        if (!friendship.getAddressee().getId().equals(userIdDoLogado)) {
            throw new AccessDeniedException("Only the addressee can reject the friend request");
        }
        requirePending(friendship);

        friendship.setStatus(FriendshipStatus.REJECTED);
        friendship.setCreatedAt(LocalDateTime.now());
        Friendship savedFriendship = friendshipRepository.save(friendship);
        publishToBoth("friendship.request.rejected", savedFriendship);
    }

    @Transactional
    public void acceptFriendRequest(Long friendshipId, String loggedUserId) {
        requireActiveUser(loggedUserId);
        Friendship friendship = getFriendship(friendshipId);

        if (!friendship.getAddressee().getId().equals(loggedUserId)) {
            throw new AccessDeniedException("Only the addressee can accept the friend request");
        }
        requirePending(friendship);

        friendship.setStatus(FriendshipStatus.ACCEPTED);
        Friendship savedFriendship = friendshipRepository.save(friendship);
        publishToBoth("friendship.request.accepted", savedFriendship);
    }

    @Transactional(readOnly = true)
    public PageResponseDTO<UserResponseDTO> getUserFriends(String userId, PaginationRequestDTO pagination) {
        User user = userRepository.findActiveById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        var pageable = pagination.toPageable(
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));

        return PageResponseDTO.from(friendshipRepository.findAllFriends(user, pageable)
                .map(friendship -> friendship.getRequester().equals(user)
                        ? UserResponseDTO.from(friendship.getAddressee())
                        : UserResponseDTO.from(friendship.getRequester())));
    }

    @Transactional(readOnly = true)
    public PageResponseDTO<FriendshipResponseDTO> getPendingRequests(String userId,
                                                                      PaginationRequestDTO pagination) {
        User user = userRepository.findActiveById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        var pageable = pagination.toPageable(
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        return PageResponseDTO.from(friendshipRepository
                .findPendingRequests(user, FriendshipStatus.PENDING, pageable)
                .map(request -> new FriendshipResponseDTO(request.getId(), request.getRequester().getName(),
                        request.getRequester().getId())));
    }

    private void enforceRejectedRequestCooldown(Friendship friendship, LocalDateTime now) {
        if (friendship.getCreatedAt() == null) {
            return;
        }

        LocalDateTime retryAt = friendship.getCreatedAt().plus(REJECTED_REQUEST_COOLDOWN);
        if (now.isBefore(retryAt)) {
            Duration remaining = Duration.between(now, retryAt);
            long retryAfterSeconds = remaining.getSeconds() + (remaining.getNano() == 0 ? 0 : 1);
            throw new RateLimitExceededException(Math.max(1, retryAfterSeconds));
        }
    }

    private void requireActiveUser(String userId) {
        userRepository.findActiveById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
    }

    private Friendship getFriendship(Long friendshipId) {
        return friendshipRepository.findById(friendshipId)
                .orElseThrow(() -> new InvalidRequestException("Friend request not found"));
    }

    private void requirePending(Friendship friendship) {
        if (friendship.getStatus() != FriendshipStatus.PENDING) {
            throw new ResourceConflictException("Friend request is no longer pending");
        }
    }

    private void publishToBoth(String type, Friendship friendship) {
        publish(type, friendship, List.of(friendship.getRequester().getEmail(), friendship.getAddressee().getEmail()));
    }

    private void publish(String type, Friendship friendship, List<String> recipients) {
        eventPublisher.publishEvent(SocialNotificationEvent.friendships(recipients,
                FriendshipEventDTO.from(type, friendship)));
    }

}
