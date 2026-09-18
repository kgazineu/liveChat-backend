package com.example.liveChat.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Evento STOMP de presença de mídia recebido em /user/queue/media-presence")
public record MediaPresenceEventDTO(
        @Schema(description = "Tipo do evento", example = "media.participant.joined") String type,
        MediaSessionResponseDTO participant) {
}
