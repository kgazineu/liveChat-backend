package com.example.liveChat.dto;

import com.example.liveChat.models.ServerInvite;
import com.example.liveChat.models.ServerInviteStatus;

import java.time.Instant;

public record ServerInviteEventDTO(
        String type,
        Long inviteId,
        String serverId,
        String serverName,
        String inviterId,
        String inviterName,
        String inviteeId,
        String inviteeName,
        ServerInviteStatus status,
        Instant createdAt) implements SocialEventPayload {

    public static ServerInviteEventDTO from(String type, ServerInvite invite) {
        return new ServerInviteEventDTO(type, invite.getId(), invite.getServer().getId(), invite.getServer().getName(),
                invite.getInviter().getId(), invite.getInviter().getName(), invite.getInvitee().getId(),
                invite.getInvitee().getName(), invite.getStatus(), invite.getCreatedAt());
    }
}
