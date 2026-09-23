package com.example.liveChat.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Confirmação de atualização do perfil")
public record ProfileUpdateConfirmDTO(
        @Schema(description = "Token opaco recebido no e-mail atual") String token
) {}
