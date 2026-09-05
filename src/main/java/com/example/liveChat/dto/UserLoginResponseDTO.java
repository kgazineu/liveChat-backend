package com.example.liveChat.dto;


import java.time.Instant;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Tokens emitidos após login ou renovação")
public record UserLoginResponseDTO(
        @Schema(description = "Nome exibido do usuário", example = "Ana Silva") String name,
        @Schema(description = "JWT de acesso usado como Bearer nas rotas protegidas e no CONNECT STOMP", example = "eyJhbGciOiJIUzI1NiJ9...") String token,
        @Schema(description = "Instante UTC de expiração do JWT", example = "2026-09-05T20:00:00Z") Instant expiresIn,
        @Schema(description = "Token opaco usado exclusivamente na renovação em /users/refresh", example = "fQ9wVjW8P0Qm0xW6vYgR3nY9kF2uZcT8P4sL7aE1bN0") String refreshToken,
        @Schema(description = "Instante UTC de expiração do refresh token", example = "2026-09-12T18:00:00Z") Instant refreshExpiresIn
) {}
