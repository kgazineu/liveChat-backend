package com.example.liveChat.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Credencial curta para conexão do cliente à sala LiveKit autorizada")
public record LiveKitConnectionDTO(
        @Schema(description = "URL pública de sinalização WebSocket do LiveKit", example = "ws://localhost:7880") String url,
        @Schema(description = "Nome da sala determinado pelo backend", example = "server-voice-550e8400-e29b-41d4-a716-446655440000") String roomName,
        @Schema(description = "JWT LiveKit de uso curto") String token,
        @Schema(description = "Expiração UTC da credencial") Instant expiresAt) {
}
