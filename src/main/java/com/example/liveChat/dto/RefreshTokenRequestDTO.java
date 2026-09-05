package com.example.liveChat.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Token usado para obter um novo par de credenciais")
public record RefreshTokenRequestDTO(
        @Schema(description = "Refresh token retornado pelo login ou pela última renovação",
                example = "fQ9wVjW8P0Qm0xW6vYgR3nY9kF2uZcT8P4sL7aE1bN0")
        String refreshToken
) {}
