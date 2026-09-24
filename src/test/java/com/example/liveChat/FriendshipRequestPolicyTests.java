package com.example.liveChat;

import com.example.liveChat.exceptions.RateLimitExceededException;
import com.example.liveChat.infra.ratelimit.RateLimiter;
import com.example.liveChat.models.Friendship;
import com.example.liveChat.models.FriendshipStatus;
import com.example.liveChat.models.User;
import com.example.liveChat.repositories.FriendshipRepository;
import com.example.liveChat.repositories.UserRepository;
import com.example.liveChat.services.FriendshipService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FriendshipRequestPolicyTests {
    @Mock private FriendshipRepository friendships;
    @Mock private UserRepository users;
    @Mock private ApplicationEventPublisher events;
    @Mock private RateLimiter rateLimiter;

    private FriendshipService service;

    @BeforeEach
    void setUp() {
        service = new FriendshipService(friendships, users, events, rateLimiter);
    }

    @Test
    void appliesSenderAndRecipientRateLimitsWithoutUsingEmailAsTheKey() {
        User requester = user("requester-id", "requester@example.test");
        User addressee = user("addressee-id", "addressee@example.test");
        stubUsers(requester, addressee);
        when(friendships.findRelationship(requester, addressee)).thenReturn(Optional.empty());
        when(friendships.save(any(Friendship.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.sendFriendRequest(requester.getId(), addressee.getId());

        verify(rateLimiter).check("friend-request-sender", requester.getId(), 10, Duration.ofHours(1));
        verify(rateLimiter).check("friend-request-recipient", addressee.getId(), 20, Duration.ofDays(1));
        verify(rateLimiter, never()).check(any(), org.mockito.ArgumentMatchers.eq(requester.getEmail()),
                any(Integer.class), any(Duration.class));
        verify(rateLimiter, never()).check(any(), org.mockito.ArgumentMatchers.eq(addressee.getEmail()),
                any(Integer.class), any(Duration.class));
    }

    @Test
    void propagatesRateLimitRejectionBeforeAccessingPersistence() {
        RateLimitExceededException rejection = new RateLimitExceededException(60);
        doThrow(rejection).when(rateLimiter)
                .check("friend-request-sender", "requester-id", 10, Duration.ofHours(1));

        assertThatThrownBy(() -> service.sendFriendRequest("requester-id", "addressee-id"))
                .isSameAs(rejection);

        verify(rateLimiter, never()).check("friend-request-recipient", "addressee-id", 20, Duration.ofDays(1));
        verifyNoInteractions(users, friendships, events);
    }

    @Test
    void rejectionStartsTheCooldownAtTheRejectionTime() {
        User requester = user("requester-id", "requester@example.test");
        User addressee = user("addressee-id", "addressee@example.test");
        Friendship pending = new Friendship(requester, addressee);
        pending.setId(1L);
        pending.setCreatedAt(LocalDateTime.now().minusDays(2));
        when(users.findActiveById(addressee.getId())).thenReturn(Optional.of(addressee));
        when(friendships.findById(pending.getId())).thenReturn(Optional.of(pending));
        when(friendships.save(pending)).thenReturn(pending);
        LocalDateTime beforeRejection = LocalDateTime.now();

        service.rejectFriendRequest(pending.getId(), addressee.getId());

        assertThat(pending.getStatus()).isEqualTo(FriendshipStatus.REJECTED);
        assertThat(pending.getCreatedAt()).isAfterOrEqualTo(beforeRejection);
    }

    @Test
    void blocksReopeningARejectedRelationshipForTwentyFourHours() {
        User requester = user("requester-id", "requester@example.test");
        User addressee = user("addressee-id", "addressee@example.test");
        Friendship rejected = new Friendship(requester, addressee);
        rejected.setStatus(FriendshipStatus.REJECTED);
        rejected.setCreatedAt(LocalDateTime.now().minusHours(23));
        stubUsers(requester, addressee);
        when(friendships.findRelationship(requester, addressee)).thenReturn(Optional.of(rejected));

        assertThatThrownBy(() -> service.sendFriendRequest(requester.getId(), addressee.getId()))
                .isInstanceOfSatisfying(RateLimitExceededException.class,
                        exception -> assertThat(exception.getRetryAfterSeconds()).isBetween(3_500L, 3_600L));

        verify(friendships, never()).save(any());
        verifyNoInteractions(events);
    }

    @Test
    void reopensARejectedRelationshipAfterCooldownAndRefreshesCreatedAt() {
        User requester = user("requester-id", "requester@example.test");
        User addressee = user("addressee-id", "addressee@example.test");
        Friendship rejected = new Friendship(addressee, requester);
        rejected.setStatus(FriendshipStatus.REJECTED);
        rejected.setCreatedAt(LocalDateTime.now().minusHours(25));
        stubUsers(requester, addressee);
        when(friendships.findRelationship(requester, addressee)).thenReturn(Optional.of(rejected));
        when(friendships.save(rejected)).thenReturn(rejected);
        LocalDateTime beforeReopening = LocalDateTime.now();

        service.sendFriendRequest(requester.getId(), addressee.getId());

        ArgumentCaptor<Friendship> saved = ArgumentCaptor.forClass(Friendship.class);
        verify(friendships).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(FriendshipStatus.PENDING);
        assertThat(saved.getValue().getRequester()).isSameAs(requester);
        assertThat(saved.getValue().getAddressee()).isSameAs(addressee);
        assertThat(saved.getValue().getCreatedAt()).isAfterOrEqualTo(beforeReopening);
    }

    private void stubUsers(User requester, User addressee) {
        when(users.findActiveById(requester.getId())).thenReturn(Optional.of(requester));
        when(users.findActiveById(addressee.getId())).thenReturn(Optional.of(addressee));
    }

    private User user(String id, String email) {
        User user = new User("Test", email, "unused");
        user.setId(id);
        return user;
    }
}
