package com.example.liveChat.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.net.URI;
import java.time.Instant;

@Schema(description = "Metadados públicos de um anexo associado à mensagem")
public record MessageAttachmentResponseDTO(
        String id,
        String originalName,
        String contentType,
        long size,
        Integer width,
        Integer height,
        @Schema(description = "URL temporária para download direto do armazenamento") URI downloadUrl,
        Instant downloadExpiresAt
) {
}
