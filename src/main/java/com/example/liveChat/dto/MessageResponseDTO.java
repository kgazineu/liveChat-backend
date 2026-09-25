package com.example.liveChat.dto;

import com.example.liveChat.models.ChannelMessage;
import com.example.liveChat.models.DirectMessage;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(description = "Mensagem persistida enviada pelo servidor via REST ou /user/queue/messages")
public record MessageResponseDTO(
        @Schema(description = "Identificador da mensagem", example = "101")
        Long id,
        @Schema(description = "UUID do canal que contém a mensagem", example = "550e8400-e29b-41d4-a716-446655440000")
        String channelId,
        @Schema(description = "Texto da mensagem", example = "Olá!")
        String content,
        @Schema(description = "UUID do autor", example = "550e8400-e29b-41d4-a716-446655440000")
        String authorId,
        @Schema(description = "Nome do autor", example = "Ana Silva")
        String authorName,
        @Schema(description = "Instante UTC de envio", example = "2026-09-05T21:00:00Z")
        Instant createdAt,
        @Schema(description = "Anexos da mensagem, com URLs temporárias de download")
        List<MessageAttachmentResponseDTO> attachments
) {
    public MessageResponseDTO {
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
    }

    public static MessageResponseDTO from(DirectMessage message, List<MessageAttachmentResponseDTO> attachments) {
        return new MessageResponseDTO(message.getId(), message.getDirectChannel().getId(), message.getContent(),
                message.getAuthor().getId(), message.getAuthor().getName(), message.getCreatedAt(), attachments);
    }

    public static MessageResponseDTO from(ChannelMessage message, List<MessageAttachmentResponseDTO> attachments) {
        return new MessageResponseDTO(message.getId(), message.getChannel().getId(), message.getContent(),
                message.getAuthor().getId(), message.getAuthor().getName(), message.getCreatedAt(), attachments);
    }
}
