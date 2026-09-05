package com.example.liveChat.infra;

import org.springframework.http.HttpStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Erro retornado pela API")
public record RestErrorMessage(
        @Schema(description = "Status HTTP", example = "UNAUTHORIZED")
        HttpStatus status,
        @Schema(description = "Descrição do erro", example = "Invalid refresh token")
        String message)
{}
