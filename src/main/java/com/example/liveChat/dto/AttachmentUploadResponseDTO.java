package com.example.liveChat.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.net.URI;
import java.time.Instant;
import java.util.Map;

@Schema(description = "Autorização temporária para enviar o arquivo diretamente ao armazenamento de objetos")
public record AttachmentUploadResponseDTO(
        @Schema(description = "UUID que deve ser enviado em attachmentIds ao criar a mensagem") String attachmentId,
        @Schema(description = "URL assinada para PUT direto") URI uploadUrl,
        @Schema(description = "Headers que devem ser enviados exatamente como retornados") Map<String, String> requiredHeaders,
        @Schema(description = "Expiração da URL de upload") Instant expiresAt
) {
}
