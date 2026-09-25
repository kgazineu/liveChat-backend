package com.example.liveChat.controllers;

import com.example.liveChat.dto.AttachmentUploadRequestDTO;
import com.example.liveChat.dto.AttachmentUploadResponseDTO;
import com.example.liveChat.infra.RestErrorMessage;
import com.example.liveChat.models.User;
import com.example.liveChat.services.MessageAttachmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Anexos")
@SecurityRequirement(name = "bearerAuth")
public class MessageAttachmentController {
    private final MessageAttachmentService attachmentService;

    public MessageAttachmentController(MessageAttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @PostMapping("/direct-channels/{channelId}/attachments/uploads")
    @Operation(summary = "Reserva upload de anexo para um canal privado",
            description = "Valida a participação e retorna uma URL PUT temporária. O arquivo não atravessa a API.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Upload autorizado",
                    content = @Content(schema = @Schema(implementation = AttachmentUploadResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Metadados, extensão, MIME ou tamanho inválido",
                    content = @Content(schema = @Schema(implementation = RestErrorMessage.class))),
            @ApiResponse(responseCode = "403", description = "Usuário não participa do canal"),
            @ApiResponse(responseCode = "404", description = "Canal inexistente"),
            @ApiResponse(responseCode = "429", description = "Limite de criação de uploads excedido"),
            @ApiResponse(responseCode = "503", description = "Armazenamento ou limitador indisponível")
    })
    public ResponseEntity<AttachmentUploadResponseDTO> reserveDirectUpload(
            @PathVariable String channelId,
            @RequestBody(required = false) AttachmentUploadRequestDTO request,
            @Parameter(hidden = true) @AuthenticationPrincipal User user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(attachmentService.reserveForDirectChannel(channelId, user, request));
    }

    @PostMapping("/servers/{serverId}/channels/{channelId}/attachments/uploads")
    @Operation(summary = "Reserva upload de anexo para um canal de texto",
            description = "Valida a participação no servidor e retorna uma URL PUT temporária. O arquivo não atravessa a API.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Upload autorizado",
                    content = @Content(schema = @Schema(implementation = AttachmentUploadResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Metadados, extensão, MIME, tamanho ou canal inválido",
                    content = @Content(schema = @Schema(implementation = RestErrorMessage.class))),
            @ApiResponse(responseCode = "403", description = "Usuário não pertence ao servidor"),
            @ApiResponse(responseCode = "404", description = "Canal inexistente"),
            @ApiResponse(responseCode = "429", description = "Limite de criação de uploads excedido"),
            @ApiResponse(responseCode = "503", description = "Armazenamento ou limitador indisponível")
    })
    public ResponseEntity<AttachmentUploadResponseDTO> reserveServerUpload(
            @PathVariable String serverId,
            @PathVariable String channelId,
            @RequestBody(required = false) AttachmentUploadRequestDTO request,
            @Parameter(hidden = true) @AuthenticationPrincipal User user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(attachmentService.reserveForServerChannel(serverId, channelId, user, request));
    }
}
