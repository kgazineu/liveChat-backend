package com.example.liveChat.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Usuário que participará de um canal privado 1:1")
public record CreateDirectChannelRequestDTO(
        @Schema(description = "UUID do outro participante", requiredMode = Schema.RequiredMode.REQUIRED)
        String participantId) {
}
