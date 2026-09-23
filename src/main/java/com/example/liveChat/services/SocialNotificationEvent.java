package com.example.liveChat.services;

import com.example.liveChat.dto.FriendshipEventDTO;
import com.example.liveChat.dto.ServerInviteEventDTO;
import com.example.liveChat.dto.ServerMemberEventDTO;
import com.example.liveChat.dto.SocialEventPayload;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

public record SocialNotificationEvent(
        List<String> recipientEmails,
        String destination,
        SocialEventPayload payload) {

    private static final String FRIENDSHIPS_DESTINATION = "/queue/friendships";
    private static final String SERVER_INVITES_DESTINATION = "/queue/server-invites";
    private static final String SERVER_MEMBERS_DESTINATION = "/queue/server-members";

    public SocialNotificationEvent {
        recipientEmails = List.copyOf(recipientEmails);
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(payload, "payload");
    }

    public static SocialNotificationEvent friendships(Collection<String> recipientEmails,
                                                        FriendshipEventDTO payload) {
        return new SocialNotificationEvent(List.copyOf(recipientEmails), FRIENDSHIPS_DESTINATION, payload);
    }

    public static SocialNotificationEvent serverInvites(Collection<String> recipientEmails,
                                                         ServerInviteEventDTO payload) {
        return new SocialNotificationEvent(List.copyOf(recipientEmails), SERVER_INVITES_DESTINATION, payload);
    }

    public static SocialNotificationEvent serverMembers(Collection<String> recipientEmails,
                                                         ServerMemberEventDTO payload) {
        return new SocialNotificationEvent(List.copyOf(recipientEmails), SERVER_MEMBERS_DESTINATION, payload);
    }
}
