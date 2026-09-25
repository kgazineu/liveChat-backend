package com.example.liveChat.controllers;

import com.example.liveChat.dto.MessageRequestDTO;
import com.example.liveChat.dto.MessageResponseDTO;
import com.example.liveChat.dto.PageResponseDTO;
import com.example.liveChat.dto.PaginationRequestDTO;
import com.example.liveChat.infra.RestErrorMessage;
import com.example.liveChat.models.User;
import com.example.liveChat.services.MessageService;
import io.swagger.v3.oas.annotations.Hidden;
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
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;


@RestController
@Tag(name = "Mensagens")
@SecurityRequirement(name = "bearerAuth")
public class MessageController {
    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    @PostMapping("/direct-channels/{channelId}/messages")
    @Operation(summary = "Envia uma mensagem em um canal privado", description = "Somente um dos dois participantes pode enviar.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Mensagem enviada",
                    content = @Content(schema = @Schema(implementation = MessageResponseDTO.class))),
            @ApiResponse(responseCode = "403", description = "Usuário não participa do canal",
                    content = @Content(schema = @Schema(implementation = RestErrorMessage.class))),
            @ApiResponse(responseCode = "404", description = "Canal inexistente",
                    content = @Content(schema = @Schema(implementation = RestErrorMessage.class))),
            @ApiResponse(responseCode = "409", description = "Upload ainda não concluído ou indisponível",
                    content = @Content(schema = @Schema(implementation = RestErrorMessage.class))),
            @ApiResponse(responseCode = "429", description = "Limite de mensagens excedido"),
            @ApiResponse(responseCode = "503", description = "Armazenamento ou limitador indisponível")
    })
    public ResponseEntity<MessageResponseDTO> sendDirectMessage(@PathVariable String channelId,
                                                                  @RequestBody MessageRequestDTO request,
                                                                  @Parameter(hidden = true) @AuthenticationPrincipal User user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(messageService.sendDirectMessage(user.getEmail(), channelId, request));
    }

    @GetMapping("/direct-channels/{channelId}/messages")
    @Operation(summary = "Lista as mensagens de um canal privado",
            description = "Somente os dois participantes podem consultá-las. Retorna as mais recentes primeiro.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Mensagens encontradas",
                    content = @Content(schema = @Schema(implementation = PageResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Paginação inválida",
                    content = @Content(schema = @Schema(implementation = RestErrorMessage.class))),
            @ApiResponse(responseCode = "403", description = "Usuário não participa do canal",
                    content = @Content(schema = @Schema(implementation = RestErrorMessage.class))),
            @ApiResponse(responseCode = "404", description = "Canal inexistente",
                    content = @Content(schema = @Schema(implementation = RestErrorMessage.class)))
    })
    public ResponseEntity<PageResponseDTO<MessageResponseDTO>> listDirectMessages(
            @PathVariable String channelId,
            @Parameter(hidden = true) @AuthenticationPrincipal User user,
            @Parameter(description = "Índice da página, iniciado em zero",
                    schema = @Schema(type = "integer", defaultValue = "0", minimum = "0"))
            @RequestParam(defaultValue = "0") String page,
            @Parameter(description = "Itens por página (máximo 100)",
                    schema = @Schema(type = "integer", defaultValue = "20", minimum = "1", maximum = "100"))
            @RequestParam(defaultValue = "20") String size) {
        return ResponseEntity.ok(messageService.listDirectMessages(
                user.getEmail(), channelId, PaginationRequestDTO.from(page, size)));
    }

    @PostMapping("/servers/{serverId}/channels/{channelId}/messages")
    @Operation(summary = "Envia uma mensagem em um canal de texto", description = "Exige participação no servidor e aceita apenas canais `TEXT`.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Mensagem enviada",
                    content = @Content(schema = @Schema(implementation = MessageResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Canal de voz ou conteúdo inválido",
                    content = @Content(schema = @Schema(implementation = RestErrorMessage.class))),
            @ApiResponse(responseCode = "403", description = "Usuário não é membro do servidor",
                    content = @Content(schema = @Schema(implementation = RestErrorMessage.class))),
            @ApiResponse(responseCode = "404", description = "Canal inexistente ou não pertence ao servidor",
                    content = @Content(schema = @Schema(implementation = RestErrorMessage.class))),
            @ApiResponse(responseCode = "409", description = "Upload ainda não concluído ou indisponível",
                    content = @Content(schema = @Schema(implementation = RestErrorMessage.class))),
            @ApiResponse(responseCode = "429", description = "Limite de mensagens excedido"),
            @ApiResponse(responseCode = "503", description = "Armazenamento ou limitador indisponível")
    })
    public ResponseEntity<MessageResponseDTO> sendChannelMessage(@PathVariable String serverId,
                                                                   @PathVariable String channelId,
                                                                   @RequestBody MessageRequestDTO request,
                                                                   @Parameter(hidden = true) @AuthenticationPrincipal User user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(messageService.sendChannelMessage(user.getEmail(), serverId, channelId, request));
    }

    @GetMapping("/servers/{serverId}/channels/{channelId}/messages")
    @Operation(summary = "Lista as mensagens de um canal de texto",
            description = "Exige participação no servidor, aceita apenas canais `TEXT` e retorna as mais recentes primeiro.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Mensagens encontradas",
                    content = @Content(schema = @Schema(implementation = PageResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Canal de voz ou paginação inválida",
                    content = @Content(schema = @Schema(implementation = RestErrorMessage.class))),
            @ApiResponse(responseCode = "403", description = "Usuário não é membro do servidor",
                    content = @Content(schema = @Schema(implementation = RestErrorMessage.class))),
            @ApiResponse(responseCode = "404", description = "Canal inexistente ou não pertence ao servidor",
                    content = @Content(schema = @Schema(implementation = RestErrorMessage.class)))
    })
    public ResponseEntity<PageResponseDTO<MessageResponseDTO>> listChannelMessages(
            @PathVariable String serverId,
            @PathVariable String channelId,
            @Parameter(hidden = true) @AuthenticationPrincipal User user,
            @Parameter(description = "Índice da página, iniciado em zero",
                    schema = @Schema(type = "integer", defaultValue = "0", minimum = "0"))
            @RequestParam(defaultValue = "0") String page,
            @Parameter(description = "Itens por página (máximo 100)",
                    schema = @Schema(type = "integer", defaultValue = "20", minimum = "1", maximum = "100"))
            @RequestParam(defaultValue = "20") String size) {
        return ResponseEntity.ok(messageService.listChannelMessages(
                user.getEmail(), serverId, channelId, PaginationRequestDTO.from(page, size)));
    }

    @MessageMapping("/direct-channels/{channelId}/messages")
    @Hidden
    public void processDirectMessage(@DestinationVariable String channelId, @Payload MessageRequestDTO request,
                                     Principal principal) {
        messageService.sendDirectMessage(principal.getName(), channelId, request);
    }

    @MessageMapping("/servers/{serverId}/channels/{channelId}/messages")
    @Hidden
    public void processChannelMessage(@DestinationVariable String serverId, @DestinationVariable String channelId,
                                      @Payload MessageRequestDTO request, Principal principal) {
        messageService.sendChannelMessage(principal.getName(), serverId, channelId, request);
    }
}
