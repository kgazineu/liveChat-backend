package com.example.liveChat.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.net.URI;
import java.time.Instant;
import java.util.Map;

@Schema(description = "Autorização temporária para enviar o arquivo diretamente ao Cloudinary")
public record AttachmentUploadResponseDTO(
        @Schema(description = "UUID que deve ser enviado em attachmentIds ao criar a mensagem") String attachmentId,
        @Schema(description = "Endpoint do Cloudinary que receberá o formulário multipart") URI uploadUrl,
        @Schema(description = "Método HTTP do upload direto", example = "POST") String uploadMethod,
        @Schema(description = "Campos assinados que devem ser adicionados ao FormData antes do campo file") Map<String, String> formFields,
        @Schema(description = "Expiração da assinatura de upload do Cloudinary") Instant expiresAt
) {
}
