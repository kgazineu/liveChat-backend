package com.example.liveChat.dto;

import com.example.liveChat.models.User;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Dados privados do usuário autenticado")
public record CurrentUserResponseDTO(
        @Schema(description = "UUID do usuário", example = "550e8400-e29b-41d4-a716-446655440000") String id,
        @Schema(description = "Nome exibido", example = "Ana Silva") String name,
        @Schema(description = "E-mail da própria conta", example = "ana@example.com") String email) {

    public static CurrentUserResponseDTO from(User user) {
        return new CurrentUserResponseDTO(user.getId(), user.getName(), user.getEmail());
    }
}
