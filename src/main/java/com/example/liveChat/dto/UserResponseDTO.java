package com.example.liveChat.dto;

import com.example.liveChat.models.User;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Dados públicos de um usuário")
public record UserResponseDTO(
        @Schema(description = "UUID do usuário", example = "550e8400-e29b-41d4-a716-446655440000") String id,
        @Schema(description = "Nome exibido", example = "Ana Silva") String name
) {
    public static UserResponseDTO from(User user) {
        return new UserResponseDTO(user.getId(), user.getName());
    }
}
