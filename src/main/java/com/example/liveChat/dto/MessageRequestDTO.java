package com.example.liveChat.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Conteúdo de uma mensagem enviada a um canal")
public record MessageRequestDTO(
        @Schema(description = "Texto da mensagem. Pode ser vazio quando houver anexos.", example = "Olá!")
        String content,
        @Schema(description = "Até quatro reservas de upload concluídas que serão consumidas pela mensagem")
        List<String> attachmentIds) {
}
