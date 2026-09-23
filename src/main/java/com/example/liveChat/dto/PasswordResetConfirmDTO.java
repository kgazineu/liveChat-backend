package com.example.liveChat.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Confirmação de recuperação de senha")
public record PasswordResetConfirmDTO(
        @Schema(description = "Token opaco recebido por e-mail") String token,
        @Schema(description = "Nova senha, entre 8 e 72 caracteres", format = "password") String password
) {}
