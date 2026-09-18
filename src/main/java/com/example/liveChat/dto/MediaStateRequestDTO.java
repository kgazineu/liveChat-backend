package com.example.liveChat.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Alteração parcial do estado de mídia do participante")
public record MediaStateRequestDTO(
        @Schema(description = "Microfone está transmitindo", example = "true", nullable = true) Boolean microphoneEnabled,
        @Schema(description = "Câmera está transmitindo", example = "false", nullable = true) Boolean cameraEnabled,
        @Schema(description = "Compartilhamento de tela ou janela está ativo", example = "false", nullable = true)
        Boolean screenShareEnabled) {

    public boolean isEmpty() {
        return microphoneEnabled == null && cameraEnabled == null && screenShareEnabled == null;
    }
}
