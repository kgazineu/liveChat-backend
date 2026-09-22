package com.example.liveChat.dto;

import com.example.liveChat.models.MediaChannelKind;
import com.example.liveChat.models.MediaSession;
import com.example.liveChat.models.MediaSessionStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Presença efêmera de um participante em uma chamada")
public record MediaSessionResponseDTO(
        @Schema(example = "SERVER_VOICE") MediaChannelKind channelKind,
        @Schema(description = "UUID do servidor; ausente em canais privados", nullable = true) String serverId,
        @Schema(description = "UUID do canal de voz ou do canal privado") String channelId,
        @Schema(description = "UUID do participante") String userId,
        @Schema(description = "Nome exibido do participante") String userName,
        @Schema(example = "ACTIVE") MediaSessionStatus status,
        boolean microphoneEnabled,
        boolean cameraEnabled,
        boolean screenShareEnabled,
        Instant joinedAt,
        Instant lastSeenAt,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Schema(description = "Credencial LiveKit presente somente na resposta de entrada", nullable = true)
        LiveKitConnectionDTO connection) {

    public static MediaSessionResponseDTO from(MediaSession session) {
        return new MediaSessionResponseDTO(session.channelKind(), session.serverId(), session.channelId(),
                session.userId(), session.userName(), session.status(), session.microphoneEnabled(),
                session.cameraEnabled(), session.screenShareEnabled(), session.joinedAt(), session.lastSeenAt(), null);
    }

    public MediaSessionResponseDTO withConnection(LiveKitConnectionDTO connection) {
        return new MediaSessionResponseDTO(channelKind, serverId, channelId, userId, userName, status,
                microphoneEnabled, cameraEnabled, screenShareEnabled, joinedAt, lastSeenAt, connection);
    }
}
