package com.example.liveChat.controllers;

import com.example.liveChat.dto.FriendshipRequestDTO;
import com.example.liveChat.dto.FriendshipResponseDTO;
import com.example.liveChat.dto.UserResponseDTO;
import com.example.liveChat.models.User;
import com.example.liveChat.services.FriendshipService;
import com.example.liveChat.infra.RestErrorMessage;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = UserResponseDTO.class))))
    @ApiResponse(responseCode = "401", description = "JWT ausente ou inválido")
    public ResponseEntity<List<UserResponseDTO>> getMyFriends(
            @Parameter(hidden = true) @AuthenticationPrincipal User loggedUser) {
        var friends = friendshipService.getUserFriends(loggedUser.getId());
        var response = friends.stream()
                .map(UserResponseDTO::forRegister)
                .toList();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/requests")
    @Operation(summary = "Lista solicitações de amizade pendentes", description = "Retorna solicitações recebidas pelo usuário autenticado.")
    @ApiResponse(responseCode = "200", description = "Solicitações pendentes",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = FriendshipResponseDTO.class))))
    @ApiResponse(responseCode = "401", description = "JWT ausente ou inválido")
    public ResponseEntity<List<FriendshipResponseDTO>> getPendingRequests(
            @Parameter(hidden = true) @AuthenticationPrincipal User loggedUser) {
        var requests = friendshipService.getPendingRequests(loggedUser.getId());
        var response = requests.stream()
                .map(request -> new FriendshipResponseDTO(
                        request.getId(),
                        request.getRequester().getName(),
                        request.getRequester().getId()
                ))
                .toList();

        return ResponseEntity.ok(response);
    }

}
