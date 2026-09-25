package com.example.liveChat.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Metadados usados para reservar o upload direto de um anexo")
public record AttachmentUploadRequestDTO(
        @Schema(example = "foto.png") String originalName,
        @Schema(example = "image/png") String contentType,
        @Schema(description = "Tamanho exato do arquivo em bytes", example = "245760") Long size,
        @Schema(description = "Largura declarada da imagem em pixels", example = "1280") Integer width,
        @Schema(description = "Altura declarada da imagem em pixels", example = "720") Integer height
) {
}
