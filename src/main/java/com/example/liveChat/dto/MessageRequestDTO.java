package com.example.liveChat.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Corpo do frame STOMP SEND enviado para /app/chat")
public record MessageRequestDTO(
        @Schema(description = "Texto da mensagem", example = "Olá!") String content,
        @Schema(description = "UUID do destinatário", example = "550e8400-e29b-41d4-a716-446655440000") String receiverId) {
}
