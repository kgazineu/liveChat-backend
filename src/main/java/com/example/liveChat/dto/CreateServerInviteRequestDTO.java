package com.example.liveChat.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Amigo que receberá o convite para o servidor")
public record CreateServerInviteRequestDTO(
        @Schema(description = "UUID de um amigo aceito", requiredMode = Schema.RequiredMode.REQUIRED)
        String friendId) {
}
