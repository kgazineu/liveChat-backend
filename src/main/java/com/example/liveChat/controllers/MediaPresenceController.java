package com.example.liveChat.controllers;

import com.example.liveChat.dto.MediaSessionResponseDTO;
import com.example.liveChat.dto.MediaStateRequestDTO;
import com.example.liveChat.infra.RestErrorMessage;
import com.example.liveChat.models.User;
import com.example.liveChat.services.MediaPresenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Tag(name = "Presença de mídia")
@SecurityRequirement(name = "bearerAuth")
public class MediaPresenceController {
    private final MediaPresenceService mediaPresenceService;

    public MediaPresenceController(MediaPresenceService mediaPresenceService) {
        this.mediaPresenceService = mediaPresenceService;
    }

    @PostMapping("/servers/{serverId}/channels/{channelId}/media-sessions")
    @Operation(summary = "Entra em um canal de voz", description = "Exige participação no servidor. Entrar em outro canal encerra a presença anterior do usuário. O limite é de cinco participantes simultâneos.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Presença ativa", content = @Content(schema = @Schema(implementation = MediaSessionResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Canal não é de voz", content = @Content(schema = @Schema(implementation = RestErrorMessage.class))),
            @ApiResponse(responseCode = "403", description = "Usuário não é membro", content = @Content(schema = @Schema(implementation = RestErrorMessage.class)))
    })
    public ResponseEntity<MediaSessionResponseDTO> joinServerVoiceChannel(@PathVariable String serverId,
                                                                            @PathVariable String channelId,
                                                                            @Parameter(hidden = true) @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(mediaPresenceService.joinServerVoiceChannel(serverId, channelId, user));
    }

    @GetMapping("/servers/{serverId}/channels/{channelId}/media-sessions")
    @Operation(summary = "Lista participantes de um canal de voz")
    public ResponseEntity<List<MediaSessionResponseDTO>> listServerVoiceChannelSessions(@PathVariable String serverId,
                                                                                           @PathVariable String channelId,
                                                                                           @Parameter(hidden = true) @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(mediaPresenceService.listServerVoiceChannelSessions(serverId, channelId, user));
    }

    @DeleteMapping("/servers/{serverId}/channels/{channelId}/media-sessions")
    @Operation(summary = "Sai de um canal de voz")
    public ResponseEntity<Void> leaveServerVoiceChannel(@PathVariable String serverId, @PathVariable String channelId,
                                                         @Parameter(hidden = true) @AuthenticationPrincipal User user) {
        mediaPresenceService.leaveServerVoiceChannel(serverId, channelId, user);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/servers/{serverId}/channels/{channelId}/media-sessions/me")
    @Operation(summary = "Atualiza o estado de mídia no canal de voz")
    public ResponseEntity<MediaSessionResponseDTO> updateServerVoiceChannelState(@PathVariable String serverId,
                                                                                   @PathVariable String channelId,
                                                                                   @RequestBody MediaStateRequestDTO request,
                                                                                   @Parameter(hidden = true) @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(mediaPresenceService.updateServerVoiceChannelState(serverId, channelId, request, user));
    }

    @PostMapping("/direct-channels/{channelId}/media-sessions")
    @Operation(summary = "Inicia ou entra em uma chamada privada 1:1")
    public ResponseEntity<MediaSessionResponseDTO> joinDirectChannel(@PathVariable String channelId,
                                                                       @Parameter(hidden = true) @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(mediaPresenceService.joinDirectChannel(channelId, user));
    }

    @GetMapping("/direct-channels/{channelId}/media-sessions")
    @Operation(summary = "Lista participantes da chamada privada 1:1")
    public ResponseEntity<List<MediaSessionResponseDTO>> listDirectChannelSessions(@PathVariable String channelId,
                                                                                      @Parameter(hidden = true) @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(mediaPresenceService.listDirectChannelSessions(channelId, user));
    }

    @DeleteMapping("/direct-channels/{channelId}/media-sessions")
    @Operation(summary = "Sai da chamada privada 1:1")
    public ResponseEntity<Void> leaveDirectChannel(@PathVariable String channelId,
                                                    @Parameter(hidden = true) @AuthenticationPrincipal User user) {
        mediaPresenceService.leaveDirectChannel(channelId, user);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/direct-channels/{channelId}/media-sessions/me")
    @Operation(summary = "Atualiza o estado de mídia na chamada privada 1:1")
    public ResponseEntity<MediaSessionResponseDTO> updateDirectChannelState(@PathVariable String channelId,
                                                                               @RequestBody MediaStateRequestDTO request,
                                                                               @Parameter(hidden = true) @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(mediaPresenceService.updateDirectChannelState(channelId, request, user));
    }
}
