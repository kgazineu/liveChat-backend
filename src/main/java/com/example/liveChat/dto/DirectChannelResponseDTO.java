package com.example.liveChat.dto;

import com.example.liveChat.models.DirectChannel;
import com.example.liveChat.models.User;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Canal privado entre exatamente dois usuários")
public record DirectChannelResponseDTO(
        @Schema(description = "UUID do canal privado") String id,
        @Schema(description = "UUID do outro participante") String participantId,
        @Schema(description = "Nome exibido do outro participante") String participantName,
        @Schema(description = "Instante UTC de criação") Instant createdAt) {

    public static DirectChannelResponseDTO from(DirectChannel channel, String currentUserId) {
        User otherParticipant = channel.otherParticipant(currentUserId);
        return new DirectChannelResponseDTO(channel.getId(), otherParticipant.getId(), otherParticipant.getName(),
                channel.getCreatedAt());
    }
}
