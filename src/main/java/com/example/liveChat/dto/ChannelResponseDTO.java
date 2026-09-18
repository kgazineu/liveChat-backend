package com.example.liveChat.dto;

import com.example.liveChat.models.ChannelType;
import com.example.liveChat.models.ServerChannel;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Canal pertencente a um servidor")
public record ChannelResponseDTO(
        @Schema(description = "UUID do canal") String id,
        @Schema(description = "Nome exibido do canal", example = "geral") String name,
        @Schema(description = "Tipo do canal", example = "TEXT") ChannelType type,
        @Schema(description = "Posição de exibição iniciada em zero", example = "0") int position,
        @Schema(description = "Instante UTC de criação") Instant createdAt) {

    public static ChannelResponseDTO from(ServerChannel channel) {
        return new ChannelResponseDTO(channel.getId(), channel.getName(), channel.getType(), channel.getPosition(),
                channel.getCreatedAt());
    }
}
