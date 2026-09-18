package com.example.liveChat.controllers;

import com.example.liveChat.dto.CreateDirectChannelRequestDTO;
import com.example.liveChat.dto.DirectChannelResponseDTO;
import com.example.liveChat.infra.RestErrorMessage;
import com.example.liveChat.models.User;
import com.example.liveChat.services.DirectChannelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/direct-channels")
@Tag(name = "Canais privados")
@SecurityRequirement(name = "bearerAuth")
public class DirectChannelController {
    private final DirectChannelService directChannelService;

    public DirectChannelController(DirectChannelService directChannelService) {
        this.directChannelService = directChannelService;
    }

    @PostMapping
    @Operation(summary = "Cria ou recupera um canal privado 1:1",
            description = "Há no máximo um canal para cada par de usuários, independentemente de qual deles inicia a conversa.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Canal privado criado",
                    content = @Content(schema = @Schema(implementation = DirectChannelResponseDTO.class))),
            @ApiResponse(responseCode = "200", description = "Canal privado já existente",
                    content = @Content(schema = @Schema(implementation = DirectChannelResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Destinatário ausente ou igual ao usuário autenticado",
                    content = @Content(schema = @Schema(implementation = RestErrorMessage.class))),
            @ApiResponse(responseCode = "404", description = "Destinatário inexistente",
                    content = @Content(schema = @Schema(implementation = RestErrorMessage.class)))
    })
    public ResponseEntity<DirectChannelResponseDTO> createOrGet(@RequestBody CreateDirectChannelRequestDTO request,
                                                                  @Parameter(hidden = true) @AuthenticationPrincipal User user) {
        DirectChannelService.CreateOrGetResult result = directChannelService.createOrGet(request, user);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.channel());
    }

    @GetMapping
    @Operation(summary = "Lista os canais privados do usuário autenticado")
    @ApiResponse(responseCode = "200", description = "Canais privados encontrados",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = DirectChannelResponseDTO.class))))
    public ResponseEntity<List<DirectChannelResponseDTO>> list(
            @Parameter(hidden = true) @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(directChannelService.list(user));
    }

    @GetMapping("/{channelId}")
    @Operation(summary = "Consulta um canal privado", description = "Somente os dois participantes podem consultá-lo.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Canal privado encontrado"),
            @ApiResponse(responseCode = "403", description = "Usuário não participa do canal",
                    content = @Content(schema = @Schema(implementation = RestErrorMessage.class))),
            @ApiResponse(responseCode = "404", description = "Canal inexistente",
                    content = @Content(schema = @Schema(implementation = RestErrorMessage.class)))
    })
    public ResponseEntity<DirectChannelResponseDTO> get(@PathVariable String channelId,
                                                          @Parameter(hidden = true) @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(directChannelService.get(channelId, user));
    }
}
