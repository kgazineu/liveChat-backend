package com.example.liveChat.dto;

public record ServerMemberEventDTO(
        String type,
        String serverId,
        ServerMemberResponseDTO member) implements SocialEventPayload {
}
