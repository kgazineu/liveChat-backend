package com.example.liveChat.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Dados necessários para criar uma conta")
public record UserRegisterDTO(
        @Schema(description = "Nome exibido", example = "Ana Silva")
        String name,
        @Schema(description = "E-mail usado como identificador de login", example = "ana@example.com")
        String email,
        @Schema(description = "Senha em texto puro enviada somente nesta requisição", example = "senha-segura", format = "password")
        String password
) {}
