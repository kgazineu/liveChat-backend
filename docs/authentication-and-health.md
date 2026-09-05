# Autenticação, WebSocket e health check

## Login e renovação

`POST /users/login` continua recebendo `email` e `password`. A resposta mantém
`name`, `token` e `expiresIn` e acrescenta `refreshToken` e `refreshExpiresIn`.
Os campos de expiração são instantes UTC (ISO 8601), seguindo o contrato existente,
e não durações em segundos.

- `token`: JWT de acesso, válido por 2 horas, usado no header HTTP
  `Authorization: Bearer <token>` e no frame STOMP `CONNECT`.
- `refreshToken`: valor aleatório de 256 bits, válido por 7 dias, usado apenas
  para renovação. Somente seu hash SHA-256 é persistido em `TB_REFRESH_TOKEN`.

Para renovar, inclusive depois da expiração do JWT:

```http
POST /users/refresh
Content-Type: application/json

{"refreshToken":"<refreshToken recebido no login>"}
```

Essa rota dispensa JWT de acesso. A resposta tem o mesmo formato do login e traz
um novo par de tokens. O cliente deve substituir ambos e serializar as tentativas
de renovação: o refresh token anterior deixa de funcionar após o uso. A rotação
ocorre em transação com bloqueio pessimista, para que duas requisições simultâneas
não consumam o mesmo token. A validade de 7 dias reinicia a cada rotação.

Tokens ausentes, inválidos, expirados ou já utilizados retornam `401`. Um JWT de
acesso não serve como refresh token, e um refresh token não autentica chamadas
HTTP ou WebSocket. Credenciais de login incorretas também retornam `401`.
O JWT anterior continua válido até expirar; a rotação invalida o refresh token.

A persistência segue o JPA e o `ddl-auto=update` já usados pelo projeto: ao iniciar,
o Hibernate cria a tabela de refresh tokens. Nenhuma variável nova é necessária.
Sessões de login anteriores à mudança precisam de novo login para obter um refresh
token. Não há endpoint de logout nem limpeza automática de registros expirados.

## Exclusão de conta e filtro HTTP

`DELETE /users/{id}` exige JWT e permite excluir apenas a própria conta. Tentar
excluir outra conta retorna `403`, antes de acessar a conta alvo. A exclusão remove
os refresh tokens da conta na mesma transação. As restrições existentes de chaves
estrangeiras de mensagens e amizades continuam aplicáveis; não foi introduzida
exclusão em cascata do histórico.

O filtro aceita somente o prefixo `Bearer ` e trata usuário inexistente como não
autenticado. JWT de conta removida retorna `401` nas rotas protegidas, sem produzir
um erro interno por incompatibilidade de exceções.

## WebSocket

O handshake HTTP em `/ws` é público para permitir clientes de navegador.
A autenticação é obrigatória no frame STOMP `CONNECT` (ou `STOMP`):

```text
Authorization:Bearer <JWT de acesso>
```

Sem credenciais válidas, o servidor rejeita a conexão STOMP com `ERROR`.
Depois da autenticação, a identidade fica associada à sessão pelo Spring.

| Operação | Destino permitido |
| --- | --- |
| `SEND` | `/app/chat` |
| `SUBSCRIBE` | `/user/queue/messages` |

Destinos diretos do broker (`/queue/**`), assinaturas de outro usuário e demais
comandos de aplicação são rejeitados. `UNSUBSCRIBE`, heartbeats autenticados e
`DISCONNECT` de limpeza continuam permitidos. O remetente vem do principal
autenticado; o serviço persiste a mensagem e a envia ao remetente e ao destinatário.

O JWT é validado na conexão. Expiração/remoção da conta não encerra automaticamente
uma conexão já aberta; ao reconectar, o cliente precisa apresentar credenciais
válidas. A configuração de origens permanece como estava e ainda precisa ser
alinhada com os domínios de produção.

## Saúde

`GET /actuator/health` é público e retorna apenas o estado agregado:

- `200` com `{"status":"UP"}` quando os indicadores estão saudáveis.
- `503` com `{"status":"DOWN"}` quando um indicador, como o banco, está indisponível.

O Actuator verifica a conexão com o banco e o espaço em disco. Detalhes,
componentes e outros endpoints do Actuator não são expostos por HTTP. Esse check
não valida o Cloudflare Tunnel nem substitui um teste de envio WebSocket.

## Verificação

```sh
./mvnw verify
```

Os testes usam Java 21 e o perfil `test`, com H2 em modo PostgreSQL, segredo de teste
e integração automática com Docker Compose desabilitada. Cobrem login, rotação,
reuso, expiração, concorrência, exclusão, credenciais HTTP, regras STOMP e entrega
real entre dois clientes WebSocket. O teste de indisponibilidade simula o indicador
do banco; o de saúde positiva usa a conexão real com H2.

H2 permite executar a suíte sem infraestrutura Docker. O comportamento específico
de bloqueios e DDL no PostgreSQL deverá ser validado na etapa de integração com o
banco de produção.

Referências: [autenticação STOMP no Spring Framework 6.2](https://docs.spring.io/spring-framework/reference/6.2/web/websocket/stomp/authentication-token-based.html)
e [endpoints do Actuator no Spring Boot 3.5](https://docs.spring.io/spring-boot/3.5/reference/actuator/endpoints.html).
