package com.example.liveChat.dto;

import com.example.liveChat.models.ServerInvite;
import com.example.liveChat.models.ServerInviteStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Convite de servidor direcionado a um amigo")
public record ServerInviteResponseDTO(
        @Schema(description = "Identificador numérico do convite") Long id,
        @Schema(description = "UUID do servidor") String serverId,
        @Schema(description = "Nome do servidor") String serverName,
        @Schema(description = "UUID de quem enviou o convite") String inviterId,
        @Schema(description = "Nome de quem enviou o convite") String inviterName,
        @Schema(description = "Estado do convite", example = "PENDING") ServerInviteStatus status,
        @Schema(description = "Instante UTC de criação") Instant createdAt) {

    public static ServerInviteResponseDTO from(ServerInvite invite) {
        return new ServerInviteResponseDTO(invite.getId(), invite.getServer().getId(), invite.getServer().getName(),
                invite.getInviter().getId(), invite.getInviter().getName(), invite.getStatus(), invite.getCreatedAt());
    }
}
