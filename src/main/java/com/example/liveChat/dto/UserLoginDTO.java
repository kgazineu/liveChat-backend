package com.example.liveChat.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Credenciais de acesso")
public record UserLoginDTO(
        @Schema(description = "E-mail cadastrado", example = "ana@example.com") String email,
        @Schema(description = "Senha da conta", example = "senha-segura", format = "password") String password
) {}
