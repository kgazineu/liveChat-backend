package com.example.liveChat.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Dados necessários para criar um servidor")
public record CreateServerRequestDTO(
        @Schema(description = "Nome exibido do servidor", example = "Equipe de produto") String name) {
}
