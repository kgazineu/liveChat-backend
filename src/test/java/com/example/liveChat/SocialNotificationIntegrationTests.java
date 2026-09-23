package com.example.liveChat;

import com.example.liveChat.dto.CreateServerInviteRequestDTO;
import com.example.liveChat.dto.CreateServerRequestDTO;
import com.example.liveChat.dto.FriendshipEventDTO;
import com.example.liveChat.dto.ServerInviteEventDTO;
import com.example.liveChat.dto.ServerMemberEventDTO;
import com.example.liveChat.exceptions.ResourceConflictException;
import com.example.liveChat.models.Friendship;
import com.example.liveChat.models.FriendshipStatus;
import com.example.liveChat.models.User;
import com.example.liveChat.repositories.FriendshipRepository;
import com.example.liveChat.repositories.UserRepository;
import com.example.liveChat.services.FriendshipService;
import com.example.liveChat.services.ServerService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@SpringBootTest
@ActiveProfiles("test")
class SocialNotificationIntegrationTests {
    @Autowired private FriendshipService friendshipService;
    @Autowired private ServerService serverService;
    @Autowired private FriendshipRepository friendships;
    @Autowired private UserRepository users;
    @Autowired private PlatformTransactionManager transactionManager;
    @MockitoBean private SimpMessagingTemplate messagingTemplate;

    @Test
    void friendshipCreatedIsPublishedOnlyAfterCommitAndNotAfterRollback() {
        User requester = user();
        User addressee = user();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        transaction.executeWithoutResult(status -> {
            friendshipService.sendFriendRequest(requester.getId(), addressee.getId());
            verifyNoInteractions(messagingTemplate);
            status.setRollbackOnly();
        });
        verifyNoInteractions(messagingTemplate);

        transaction.executeWithoutResult(status -> {
            friendshipService.sendFriendRequest(requester.getId(), addressee.getId());
            verifyNoInteractions(messagingTemplate);
        });

        var payload = ArgumentCaptor.forClass(FriendshipEventDTO.class);
        verify(messagingTemplate).convertAndSendToUser(eq(addressee.getEmail()), eq("/queue/friendships"),
                payload.capture());
        assertThat(payload.getValue().type()).isEqualTo("friendship.request.created");
        assertThat(payload.getValue().status()).isEqualTo(FriendshipStatus.PENDING);
    }

    @Test
    void friendshipTransitionsRequirePendingAndNotifyBothUsers() {
        User requester = user();
        User addressee = user();
        friendshipService.sendFriendRequest(requester.getId(), addressee.getId());
        Friendship friendship = friendships.findRelationship(requester, addressee).orElseThrow();
        clearInvocations(messagingTemplate);

        friendshipService.acceptFriendRequest(friendship.getId(), addressee.getId());

        var acceptedPayloads = ArgumentCaptor.forClass(FriendshipEventDTO.class);
        verify(messagingTemplate).convertAndSendToUser(eq(requester.getEmail()), eq("/queue/friendships"),
                acceptedPayloads.capture());
        verify(messagingTemplate).convertAndSendToUser(eq(addressee.getEmail()), eq("/queue/friendships"),
                acceptedPayloads.capture());
        assertThat(acceptedPayloads.getAllValues())
                .allSatisfy(payload -> {
                    assertThat(payload.type()).isEqualTo("friendship.request.accepted");
                    assertThat(payload.status()).isEqualTo(FriendshipStatus.ACCEPTED);
                });
        clearInvocations(messagingTemplate);

        assertThatThrownBy(() -> friendshipService.acceptFriendRequest(friendship.getId(), addressee.getId()))
                .isInstanceOf(ResourceConflictException.class);
        assertThatThrownBy(() -> friendshipService.rejectFriendRequest(friendship.getId(), addressee.getId()))
                .isInstanceOf(ResourceConflictException.class);
        verifyNoInteractions(messagingTemplate);

        User secondRequester = user();
        User secondAddressee = user();
        friendshipService.sendFriendRequest(secondRequester.getId(), secondAddressee.getId());
        Friendship rejectedFriendship = friendships.findRelationship(secondRequester, secondAddressee).orElseThrow();
        clearInvocations(messagingTemplate);

        friendshipService.rejectFriendRequest(rejectedFriendship.getId(), secondAddressee.getId());

        var rejectedPayloads = ArgumentCaptor.forClass(FriendshipEventDTO.class);
        verify(messagingTemplate).convertAndSendToUser(eq(secondRequester.getEmail()), eq("/queue/friendships"),
                rejectedPayloads.capture());
        verify(messagingTemplate).convertAndSendToUser(eq(secondAddressee.getEmail()), eq("/queue/friendships"),
                rejectedPayloads.capture());
        assertThat(rejectedPayloads.getAllValues())
                .allSatisfy(payload -> {
                    assertThat(payload.type()).isEqualTo("friendship.request.rejected");
                    assertThat(payload.status()).isEqualTo(FriendshipStatus.REJECTED);
                });
    }

    @Test
    void serverInviteEventsTargetParticipantsAndMemberJoinedTargetsAllMembers() {
        User owner = user();
        User invitee = user();
        Friendship friendship = new Friendship(owner, invitee);
        friendship.setStatus(FriendshipStatus.ACCEPTED);
        friendships.save(friendship);
        String serverId = serverService.create(new CreateServerRequestDTO("Equipe"), owner).id();

        clearInvocations(messagingTemplate);
        var invite = serverService.inviteFriend(serverId, new CreateServerInviteRequestDTO(invitee.getId()), owner);
        var createdPayload = ArgumentCaptor.forClass(ServerInviteEventDTO.class);
        verify(messagingTemplate).convertAndSendToUser(eq(invitee.getEmail()), eq("/queue/server-invites"),
                createdPayload.capture());
        assertThat(createdPayload.getValue().type()).isEqualTo("server.invite.created");

        clearInvocations(messagingTemplate);
        serverService.acceptInvite(invite.id(), invitee);

        verify(messagingTemplate, times(4)).convertAndSendToUser(anyString(), anyString(), any());
        var acceptedPayloads = ArgumentCaptor.forClass(ServerInviteEventDTO.class);
        verify(messagingTemplate).convertAndSendToUser(eq(owner.getEmail()), eq("/queue/server-invites"),
                acceptedPayloads.capture());
        verify(messagingTemplate).convertAndSendToUser(eq(invitee.getEmail()), eq("/queue/server-invites"),
                acceptedPayloads.capture());
        assertThat(acceptedPayloads.getAllValues())
                .allSatisfy(payload -> assertThat(payload.type()).isEqualTo("server.invite.accepted"));
        var memberPayloads = ArgumentCaptor.forClass(ServerMemberEventDTO.class);
        verify(messagingTemplate).convertAndSendToUser(eq(owner.getEmail()), eq("/queue/server-members"),
                memberPayloads.capture());
        verify(messagingTemplate).convertAndSendToUser(eq(invitee.getEmail()), eq("/queue/server-members"),
                memberPayloads.capture());
        assertThat(memberPayloads.getAllValues())
                .allSatisfy(payload -> assertThat(payload.type()).isEqualTo("server.member.joined"));
        verify(messagingTemplate, never()).convertAndSendToUser(anyString(), eq("/user/queue/server-members"), any());
    }

    private User user() {
        return users.save(new User("Test", UUID.randomUUID() + "@example.test", "unused"));
    }
}
