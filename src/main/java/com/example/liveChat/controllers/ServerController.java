package com.example.liveChat.controllers;

import com.example.liveChat.dto.ChannelResponseDTO;
import com.example.liveChat.dto.CreateChannelRequestDTO;
import com.example.liveChat.dto.CreateServerRequestDTO;
import com.example.liveChat.dto.ServerResponseDTO;
import com.example.liveChat.infra.RestErrorMessage;
import com.example.liveChat.models.User;
import com.example.liveChat.services.ServerService;
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
@RequestMapping("/servers")
@Tag(name = "Servidores")
@SecurityRequirement(name = "bearerAuth")
public class ServerController {
    private final ServerService serverService;

    public ServerController(ServerService serverService) {
        this.serverService = serverService;
    }

    @PostMapping
    @Operation(summary = "Cria um servidor", description = "O usuário autenticado se torna seu proprietário e primeiro membro.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Servidor criado",
                    content = @Content(schema = @Schema(implementation = ServerResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "JWT ausente ou inválido")
    })
    public ResponseEntity<ServerResponseDTO> create(@RequestBody CreateServerRequestDTO request,
                                                      @Parameter(hidden = true) @AuthenticationPrincipal User user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(serverService.create(request, user));
    }

    @GetMapping
    @Operation(summary = "Lista os servidores do usuário autenticado")
    @ApiResponse(responseCode = "200", description = "Servidores encontrados",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = ServerResponseDTO.class))))
    public ResponseEntity<List<ServerResponseDTO>> list(@Parameter(hidden = true) @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(serverService.listFor(user));
    }

    @GetMapping("/{serverId}")
    @Operation(summary = "Consulta um servidor", description = "Exige que o usuário autenticado seja membro.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Servidor encontrado"),
            @ApiResponse(responseCode = "403", description = "Usuário não é membro",
                    content = @Content(schema = @Schema(implementation = RestErrorMessage.class))),
            @ApiResponse(responseCode = "404", description = "Servidor inexistente",
                    content = @Content(schema = @Schema(implementation = RestErrorMessage.class)))
    })
    public ResponseEntity<ServerResponseDTO> get(@PathVariable String serverId,
                                                   @Parameter(hidden = true) @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(serverService.get(serverId, user));
    }

    @PostMapping("/{serverId}/channels")
    @Operation(summary = "Cria um canal", description = "Somente o proprietário do servidor pode criar canais.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Canal criado",
                    content = @Content(schema = @Schema(implementation = ChannelResponseDTO.class))),
            @ApiResponse(responseCode = "403", description = "Usuário não é o proprietário",
                    content = @Content(schema = @Schema(implementation = RestErrorMessage.class)))
    })
    public ResponseEntity<ChannelResponseDTO> createChannel(@PathVariable String serverId,
                                                              @RequestBody CreateChannelRequestDTO request,
                                                              @Parameter(hidden = true) @AuthenticationPrincipal User user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(serverService.createChannel(serverId, request, user));
    }

    @GetMapping("/{serverId}/channels")
    @Operation(summary = "Lista os canais de um servidor", description = "Exige que o usuário autenticado seja membro.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Canais encontrados",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ChannelResponseDTO.class)))),
            @ApiResponse(responseCode = "403", description = "Usuário não é membro",
                    content = @Content(schema = @Schema(implementation = RestErrorMessage.class)))
    })
    public ResponseEntity<List<ChannelResponseDTO>> listChannels(@PathVariable String serverId,
                                                                    @Parameter(hidden = true) @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(serverService.listChannels(serverId, user));
    }
}
