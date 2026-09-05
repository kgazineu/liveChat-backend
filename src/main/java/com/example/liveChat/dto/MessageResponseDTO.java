package com.example.liveChat.dto;

import java.time.LocalDateTime;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Mensagem persistida enviada pelo servidor via REST ou /user/queue/messages")
public record MessageResponseDTO(
        @Schema(description = "Identificador da mensagem", example = "101")
        Long id,
        @Schema(description = "Texto da mensagem", example = "Olá!")
        String content,
        @Schema(description = "UUID do remetente", example = "550e8400-e29b-41d4-a716-446655440000")
        String senderId,
        @Schema(description = "Nome do remetente", example = "Ana Silva")
        String senderName,
        @Schema(description = "E-mail do remetente", example = "ana@example.com")
        String senderEmail,
        @Schema(description = "UUID do destinatário", example = "6ba7b810-9dad-41d1-80b4-00c04fd430c8")
        String receiverId,
        @Schema(description = "E-mail do destinatário", example = "bruno@example.com")
        String receiverEmail,
        @Schema(description = "Data e hora local em que a mensagem foi persistida", example = "2026-09-05T18:00:00")
        LocalDateTime timestamp
) {}
