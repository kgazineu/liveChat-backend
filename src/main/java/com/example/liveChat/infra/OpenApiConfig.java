package com.example.liveChat.infra;

import com.example.liveChat.dto.MessageRequestDTO;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {
    public static final String BEARER_AUTH = "bearerAuth";

    @Bean
    public OpenAPI liveChatOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("LiveChat API")
                        .version("1.0.0")
                        .description("API REST e contrato STOMP do LiveChat. Horários do tipo Instant são representados em UTC."))
                .tags(List.of(
                        new Tag().name("Usuários").description("Cadastro, autenticação e gerenciamento da conta."),
                        new Tag().name("Amizades").description("Solicitações e relacionamentos entre usuários."),
                        new Tag().name("Mensagens").description("Histórico de mensagens privadas."),
                        new Tag().name("Canais privados").description("Conversas privadas entre exatamente dois usuários."),
                        new Tag().name("Servidores").description("Servidores, membros, canais e convites."),
                        new Tag().name("WebSocket").description("Handshake e protocolo STOMP para mensagens em tempo real."),
                        new Tag().name("Monitoramento").description("Estado de saúde da aplicação e de suas dependências.")))
                .components(new Components()
                        .schemas(ModelConverters.getInstance().read(MessageRequestDTO.class))
                        .addSecuritySchemes(BEARER_AUTH,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("JWT de acesso retornado por /users/login ou /users/refresh.")))
                .paths(documentNonControllerEndpoints());
    }

    private Paths documentNonControllerEndpoints() {
        Schema<?> healthSchema = new ObjectSchema()
                .addProperty("status", new Schema<String>().type("string").example("UP"));

        Operation health = new Operation()
                .tags(List.of("Monitoramento"))
                .summary("Verifica a saúde da aplicação")
                .description("Endpoint público do Spring Boot Actuator. Retorna apenas o estado agregado; quando o banco estiver indisponível, responde 503.")
                .responses(new ApiResponses()
                        .addApiResponse("200", new ApiResponse().description("Aplicação e dependências disponíveis")
                                .content(new Content().addMediaType("application/vnd.spring-boot.actuator.v3+json",
                                        new MediaType().schema(healthSchema))))
                        .addApiResponse("503", new ApiResponse().description("Uma dependência essencial está indisponível")
                                .content(new Content().addMediaType("application/vnd.spring-boot.actuator.v3+json",
                                        new MediaType().schema(healthSchema)))));

        ApiResponse websocketUpgrade = new ApiResponse()
                .description("Protocolo atualizado para WebSocket. Mensagens recebidas seguem MessageResponseDTO.");
        websocketUpgrade.addExtension("x-stomp-message-schema", "#/components/schemas/MessageResponseDTO");

        Operation websocket = new Operation()
                .tags(List.of("WebSocket"))
                .summary("Abre uma conexão WebSocket para STOMP")
                .description("""
                        Handshake WebSocket público. Depois do upgrade, o cliente deve enviar um frame STOMP `CONNECT`
                        com o header nativo `Authorization: Bearer <JWT>`. Credenciais ausentes, inválidas ou expiradas
                        encerram a conexão com um frame `ERROR`.

                        Contrato autenticado:
                        - enviar mensagens: `SEND` para `/app/chat`, corpo JSON conforme o schema `MessageRequestDTO`;
                        - receber mensagens: `SUBSCRIBE` em `/user/queue/messages`;
                        - o servidor envia o schema `MessageResponseDTO` ao remetente e ao destinatário;
                        - demais destinos de envio e assinatura são rejeitados.

                        O JWT é verificado no `CONNECT`. O endpoint usa WebSocket nativo, sem SockJS.
                        """)
                .requestBody(new RequestBody()
                        .required(false)
                        .description("Referência documental ao JSON do frame STOMP `SEND`; não é um corpo do handshake HTTP.")
                        .content(new Content().addMediaType("application/json", new MediaType()
                                .schema(new Schema<>().$ref("#/components/schemas/MessageRequestDTO")))))
                .responses(new ApiResponses()
                        .addApiResponse("101", websocketUpgrade)
                        .addApiResponse("400", new ApiResponse().description("Handshake WebSocket inválido")));

        return new Paths()
                .addPathItem("/actuator/health", new PathItem().get(health))
                .addPathItem("/ws", new PathItem().get(websocket));
    }
}
