package com.example.liveChat.dto;

import com.example.liveChat.models.ChannelType;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Dados necessários para criar um canal de servidor")
public record CreateChannelRequestDTO(
        @Schema(description = "Nome exibido do canal", example = "geral") String name,
        @Schema(description = "Tipo de canal", example = "TEXT") ChannelType type) {
}
