package com.example.liveChat.dto;

public sealed interface SocialEventPayload permits FriendshipEventDTO, ServerInviteEventDTO, ServerMemberEventDTO {
    String type();
}
