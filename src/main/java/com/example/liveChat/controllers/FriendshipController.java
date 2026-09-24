package com.example.liveChat.controllers;

import com.example.liveChat.dto.FriendshipRequestDTO;
import com.example.liveChat.dto.FriendshipResponseDTO;
import com.example.liveChat.dto.PageResponseDTO;
import com.example.liveChat.dto.PaginationRequestDTO;
import com.example.liveChat.dto.UserResponseDTO;
import com.example.liveChat.models.User;
import com.example.liveChat.services.FriendshipService;
import com.example.liveChat.infra.RestErrorMessage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/friendships")
@Tag(name = "Amizades")
@SecurityRequirement(name = "bearerAuth")
public class FriendshipController {

    @Autowired
    private FriendshipService friendshipService;

    @PostMapping("/send")
    @Operation(summary = "Envia uma solicitação de amizade")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Solicitação enviada ou relacionamento rejeitado reaberto"),
            @ApiResponse(responseCode = "401", description = "JWT ausente ou inválido"),
            @ApiResponse(responseCode = "500", description = "Usuário inexistente, auto-solicitação ou relacionamento já ativo",
                    content = @Content(schema = @Schema(implementation = RestErrorMessage.class)))
    })
    public ResponseEntity<Void> sendFriendRequest(
            @RequestBody FriendshipRequestDTO body,
            @Parameter(hidden = true) @AuthenticationPrincipal User loggedUser
    ) {
        friendshipService.sendFriendRequest(loggedUser.getId(), body.targetUserId());
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{id}/accept")
    @Operation(summary = "Aceita uma solicitação de amizade", description = "Somente o destinatário pode aceitar a solicitação.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Solicitação aceita"),
            @ApiResponse(responseCode = "401", description = "JWT ausente ou inválido"),
            @ApiResponse(responseCode = "500", description = "Solicitação inexistente ou usuário sem permissão",
                    content = @Content(schema = @Schema(implementation = RestErrorMessage.class)))
    })
    public ResponseEntity<Void> acceptRequest(
            @Parameter(description = "Identificador da solicitação", required = true) @PathVariable Long id,
            @Parameter(hidden = true) @AuthenticationPrincipal User loggedUser
    ) {
        friendshipService.acceptFriendRequest(id, loggedUser.getId());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/reject")
    @Operation(summary = "Rejeita uma solicitação de amizade", description = "Somente o destinatário pode rejeitar a solicitação.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Solicitação rejeitada"),
            @ApiResponse(responseCode = "401", description = "JWT ausente ou inválido"),
            @ApiResponse(responseCode = "500", description = "Solicitação inexistente, aceita ou usuário sem permissão",
                    content = @Content(schema = @Schema(implementation = RestErrorMessage.class)))
    })
    public ResponseEntity<Void> rejectRequest(
            @Parameter(description = "Identificador da solicitação", required = true) @PathVariable Long id,
            @Parameter(hidden = true) @AuthenticationPrincipal User loggedUser
    ) {
        friendshipService.rejectFriendRequest(id, loggedUser.getId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    @Operation(summary = "Lista os amigos do usuário autenticado")
    @ApiResponse(responseCode = "200", description = "Amigos encontrados",
            content = @Content(schema = @Schema(implementation = PageResponseDTO.class)))
    @ApiResponse(responseCode = "400", description = "Paginação inválida",
            content = @Content(schema = @Schema(implementation = RestErrorMessage.class)))
    @ApiResponse(responseCode = "401", description = "JWT ausente ou inválido")
    public ResponseEntity<PageResponseDTO<UserResponseDTO>> getMyFriends(
            @Parameter(hidden = true) @AuthenticationPrincipal User loggedUser,
            @Parameter(description = "Índice da página, iniciado em zero",
                    schema = @Schema(type = "integer", defaultValue = "0", minimum = "0"))
            @RequestParam(defaultValue = "0") String page,
            @Parameter(description = "Itens por página (máximo 100)",
                    schema = @Schema(type = "integer", defaultValue = "20", minimum = "1", maximum = "100"))
            @RequestParam(defaultValue = "20") String size) {
        return ResponseEntity.ok(friendshipService.getUserFriends(
                loggedUser.getId(), PaginationRequestDTO.from(page, size)));
    }

    @GetMapping("/requests")
    @Operation(summary = "Lista solicitações de amizade pendentes", description = "Retorna solicitações recebidas pelo usuário autenticado.")
    @ApiResponse(responseCode = "200", description = "Solicitações pendentes",
            content = @Content(schema = @Schema(implementation = PageResponseDTO.class)))
    @ApiResponse(responseCode = "400", description = "Paginação inválida",
            content = @Content(schema = @Schema(implementation = RestErrorMessage.class)))
    @ApiResponse(responseCode = "401", description = "JWT ausente ou inválido")
    public ResponseEntity<PageResponseDTO<FriendshipResponseDTO>> getPendingRequests(
            @Parameter(hidden = true) @AuthenticationPrincipal User loggedUser,
            @Parameter(description = "Índice da página, iniciado em zero",
                    schema = @Schema(type = "integer", defaultValue = "0", minimum = "0"))
            @RequestParam(defaultValue = "0") String page,
            @Parameter(description = "Itens por página (máximo 100)",
                    schema = @Schema(type = "integer", defaultValue = "20", minimum = "1", maximum = "100"))
            @RequestParam(defaultValue = "20") String size) {
        return ResponseEntity.ok(friendshipService.getPendingRequests(
                loggedUser.getId(), PaginationRequestDTO.from(page, size)));
    }

}
