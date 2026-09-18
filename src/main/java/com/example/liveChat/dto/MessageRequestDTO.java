package com.example.liveChat.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Conteúdo de uma mensagem enviada a um canal")
public record MessageRequestDTO(
        @Schema(description = "Texto da mensagem", example = "Olá!", requiredMode = Schema.RequiredMode.REQUIRED)
        String content) {
}
