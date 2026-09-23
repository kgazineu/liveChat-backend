package com.example.liveChat.dto;

import com.example.liveChat.models.ServerMember;
import com.example.liveChat.models.ServerRole;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Membro de um servidor")
public record ServerMemberResponseDTO(
        @Schema(description = "UUID do usuário") String userId,
        @Schema(description = "Nome exibido do usuário") String userName,
        @Schema(description = "E-mail do usuário") String userEmail,
        @Schema(description = "Papel do usuário no servidor", example = "MEMBER") ServerRole role,
        @Schema(description = "Instante UTC de entrada no servidor") Instant joinedAt) {

    public static ServerMemberResponseDTO from(ServerMember member) {
        return new ServerMemberResponseDTO(member.getUser().getId(), member.getUser().getName(),
                member.getUser().getEmail(), member.getRole(), member.getJoinedAt());
    }
}
