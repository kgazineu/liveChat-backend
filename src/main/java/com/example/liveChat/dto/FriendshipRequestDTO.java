package com.example.liveChat.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Destinatário de uma solicitação de amizade")
public record FriendshipRequestDTO(
        @Schema(description = "UUID do usuário que receberá a solicitação", example = "550e8400-e29b-41d4-a716-446655440000")
        String targetUserId) {
}
