package com.example.liveChat.dto;

import com.example.liveChat.models.Friendship;
import com.example.liveChat.models.FriendshipStatus;

import java.time.LocalDateTime;

public record FriendshipEventDTO(
        String type,
        Long friendshipId,
        String requesterId,
        String requesterName,
        String addresseeId,
        String addresseeName,
        FriendshipStatus status,
        LocalDateTime createdAt) implements SocialEventPayload {

    public static FriendshipEventDTO from(String type, Friendship friendship) {
        return new FriendshipEventDTO(type, friendship.getId(), friendship.getRequester().getId(),
                friendship.getRequester().getName(), friendship.getAddressee().getId(),
                friendship.getAddressee().getName(), friendship.getStatus(), friendship.getCreatedAt());
    }
}
