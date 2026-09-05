package com.example.liveChat.controllers;

import com.example.liveChat.dto.UserLoginDTO;
import com.example.liveChat.dto.UserLoginResponseDTO;
import com.example.liveChat.dto.UserRegisterDTO;
import com.example.liveChat.dto.UserResponseDTO;
import com.example.liveChat.dto.RefreshTokenRequestDTO;
import com.example.liveChat.models.User;
import com.example.liveChat.services.UserService;
import com.example.liveChat.services.RefreshTokenService;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/users")
@Tag(name = "Usuários")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @PostMapping("/register")
    @Operation(summary = "Cadastra um usuário", description = "Cria uma conta e armazena a senha com BCrypt. Não inicia uma sessão automaticamente.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Usuário criado"),
            @ApiResponse(responseCode = "409", description = "E-mail já cadastrado",
                    content = @Content(schema = @Schema(implementation = RestErrorMessage.class)))
    })
    public ResponseEntity<UserResponseDTO> register(@RequestBody UserRegisterDTO body) {
        var newUser = userService.register(body);
        return ResponseEntity.ok(UserResponseDTO.forRegister(newUser));
    }

    @PostMapping("/login")
    @Operation(summary = "Autentica um usuário", description = "Retorna um JWT de acesso válido por 2 horas e um refresh token válido por 7 dias.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Credenciais válidas"),
            @ApiResponse(responseCode = "401", description = "E-mail ou senha inválidos",
                    content = @Content(schema = @Schema(implementation = RestErrorMessage.class)))
    })
    public ResponseEntity<UserLoginResponseDTO> login(@RequestBody UserLoginDTO body){
        UserLoginResponseDTO token = userService.login(body);
        return ResponseEntity.ok(token);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Renova os tokens", description = "Consome o refresh token atual e devolve um novo par. O token consumido não pode ser reutilizado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tokens renovados"),
            @ApiResponse(responseCode = "401", description = "Refresh token ausente, inválido, expirado ou já utilizado",
                    content = @Content(schema = @Schema(implementation = RestErrorMessage.class)))
    })
    public ResponseEntity<UserLoginResponseDTO> refresh(@RequestBody RefreshTokenRequestDTO body) {
        return ResponseEntity.ok(refreshTokenService.refresh(body.refreshToken()));
    }

    @GetMapping
    @Operation(summary = "Lista os usuários", description = "Retorna todas as contas cadastradas.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "200", description = "Usuários encontrados",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = UserResponseDTO.class))))
    @ApiResponse(responseCode = "401", description = "JWT ausente ou inválido")
    public ResponseEntity<List<UserResponseDTO>> getAllUsers() {
        var users = userService.findAll();
        var response = users.stream().map(UserResponseDTO::forRegister).toList();

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("{id}")
    @Operation(summary = "Exclui a própria conta", description = "O id deve pertencer ao usuário autenticado.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Conta excluída"),
            @ApiResponse(responseCode = "401", description = "JWT ausente ou inválido"),
            @ApiResponse(responseCode = "403", description = "Tentativa de excluir outra conta",
                    content = @Content(schema = @Schema(implementation = RestErrorMessage.class))),
            @ApiResponse(responseCode = "404", description = "Usuário não encontrado",
                    content = @Content(schema = @Schema(implementation = RestErrorMessage.class)))
    })
    public ResponseEntity<Void> deleteUser(
            @Parameter(description = "UUID da conta autenticada", required = true) @PathVariable String id,
            @Parameter(hidden = true) @AuthenticationPrincipal User loggedUser){
        userService.deleteUser(id, loggedUser.getId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/search")
    @Operation(summary = "Busca usuário por e-mail", description = "A implementação atual realiza correspondência exata do e-mail.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista vazia ou usuário encontrado",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = UserResponseDTO.class)))),
            @ApiResponse(responseCode = "401", description = "JWT ausente ou inválido")
    })
    public ResponseEntity<List<UserResponseDTO>> searchUsers(
            @Parameter(description = "E-mail completo", example = "ana@example.com", required = true) @RequestParam String email){
        var userFound = userService.searchUsersPartial(email);
        return ResponseEntity.ok(userFound);
    }

    @GetMapping("/me")
    @Operation(summary = "Obtém o usuário autenticado", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Dados públicos da conta autenticada"),
            @ApiResponse(responseCode = "401", description = "JWT ausente ou inválido")
    })
    public ResponseEntity<UserResponseDTO> me(@Parameter(hidden = true) Authentication authentication) {
        UserResponseDTO user = userService.getAuthenticatedUser(authentication);
        return ResponseEntity.ok(user);
    }
}
