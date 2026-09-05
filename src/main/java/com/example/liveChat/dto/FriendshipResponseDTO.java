package com.example.liveChat.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Solicitação de amizade pendente")
public record FriendshipResponseDTO(
        @Schema(description = "Identificador da solicitação", example = "42") Long id,
        @Schema(description = "Nome de quem enviou a solicitação", example = "Ana Silva") String requesterName,
        @Schema(description = "UUID de quem enviou a solicitação", example = "550e8400-e29b-41d4-a716-446655440000") String requesterId) {
}
