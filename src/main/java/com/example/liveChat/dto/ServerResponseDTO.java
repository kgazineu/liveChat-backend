package com.example.liveChat.dto;

import com.example.liveChat.models.Server;
import com.example.liveChat.models.ServerMember;
import com.example.liveChat.models.ServerRole;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Servidor visível ao membro autenticado")
public record ServerResponseDTO(
        @Schema(description = "UUID do servidor") String id,
        @Schema(description = "Nome exibido do servidor", example = "Equipe de produto") String name,
        @Schema(description = "UUID do proprietário") String ownerId,
        @Schema(description = "Papel do usuário autenticado no servidor", example = "OWNER") ServerRole role,
        @Schema(description = "Instante UTC de criação") Instant createdAt) {

    public static ServerResponseDTO from(Server server, ServerMember member) {
        return new ServerResponseDTO(server.getId(), server.getName(), server.getOwner().getId(), member.getRole(),
                server.getCreatedAt());
    }
}
