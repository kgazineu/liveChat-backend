package com.example.liveChat.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Alterações pendentes do perfil autenticado")
public record ProfileUpdateRequestDTO(
        @Schema(description = "Novo nome exibido, após remoção de espaços nas extremidades", example = "Ana Silva")
        String name,
        @Schema(description = "Novo e-mail, normalizado para minúsculas", example = "ana@example.com")
        String email
) {}
