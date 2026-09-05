package com.example.liveChat.controllers;

import com.example.liveChat.dto.MessageRequestDTO;
import com.example.liveChat.dto.MessageResponseDTO;
import com.example.liveChat.services.MessageService;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

import java.security.Principal;
import java.util.List;

@Controller
@Tag(name = "Mensagens")
public class MessageController {

    @Autowired
    private MessageService messageService;

    @MessageMapping("/chat")
    @Hidden
    public void processMessage(@Payload MessageRequestDTO messageRequest, Principal principal) {
        messageService.sendMessage(principal.getName(), messageRequest);
    }

    @GetMapping("/messages/{targetUserId}")
    @ResponseBody
    @Operation(summary = "Obtém o histórico entre dois usuários",
            description = "Retorna em ordem cronológica as mensagens trocadas entre o usuário autenticado e o usuário informado.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Histórico encontrado",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = MessageResponseDTO.class)))),
            @ApiResponse(responseCode = "401", description = "JWT ausente ou inválido"),
            @ApiResponse(responseCode = "500", description = "Usuário de destino inexistente")
    })
    public ResponseEntity<List<MessageResponseDTO>> getChatHistory(
            @Parameter(description = "UUID do outro participante", required = true) @PathVariable String targetUserId,
            @Parameter(hidden = true) Principal principal) {
        List<MessageResponseDTO> history = messageService.getChatHistory(principal.getName(), targetUserId);
        return ResponseEntity.ok(history);
    }
}
