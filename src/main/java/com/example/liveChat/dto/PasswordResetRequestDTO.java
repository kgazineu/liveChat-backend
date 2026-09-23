package com.example.liveChat.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Solicitação de recuperação de senha")
public record PasswordResetRequestDTO(
        @Schema(description = "E-mail da conta", example = "ana@example.com") String email
) {}
