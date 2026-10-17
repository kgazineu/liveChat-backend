# benchmark — load test do liveChat (servidor próprio)

Testa o teto do **seu próprio** servidor: onde a latência dispara, onde começam os erros,
quantas conexões WebSocket aguenta. Medição, não flood cego. Roda com [k6](https://k6.io).

## Pré-requisitos
- **k6**: `brew install k6`
- **Node 18+** (para o seed — usa `fetch` nativo)

## Rotas/protocolo reais (confirmados no código)
- Login: `POST /users/login` `{email,password}` → `{ name, **token**, expiresIn, refreshToken, ... }`
- Registro: `POST /users/register` `{name,email,password}` (senha 8–72 chars, sem outra regra)
- WS: `wss://.../ws` — **STOMP 1.2 puro (sem SockJS)**, auth no frame `CONNECT` via header `Authorization: Bearer <jwt>`
- SEND: `/app/direct-channels/{uuid}/messages`, corpo `{"content":"..."}` — o destino é validado por regex, só funciona num canal onde o usuário participa (por isso o seed cria os canais)

## 1. Seed (rodar 1x)
Cria o pool fixo de contas + um canal direto por conta + uma solicitação de amizade
de cada conta para `INVITE_TARGET`, e grava `.pool.json`.
```bash
node benchmark/seed.mjs
# pool grande (mais linhas no banco), com mais paralelismo:
CONCURRENCY=50 POOL_SIZE=10000 node benchmark/seed.mjs
```
Pool padrão: 50 contas `loadtest+<i>@example.com`. Tokens gravados valem ~2h — rode de novo se expirarem.
A conta `INVITE_TARGET` (padrão `kaiangazineu@kaian.com`) precisa existir, senão essa etapa é pulada com aviso.

## 2. Teste HTTP (teto de CPU/conexão)
Rampa de VUs batendo em `login` (BCrypt+JWT, o caminho caro) + `GET /users/me`.
```bash
k6 run benchmark/stress.js
# mais forte:
VUS_PEAK=2000 k6 run benchmark/stress.js
```

## 3. Teste WebSocket (teto de conexões + mensagens)
Abre N conexões STOMP e manda mensagens de chat contínuas.
```bash
k6 run benchmark/ws-stress.js
# mais forte:
WS_PEAK=3000 WS_MSG_INTERVAL_MS=200 k6 run benchmark/ws-stress.js
```

## Knobs (env)
| var | default | onde |
|-----|---------|------|
| `API` / `WS_URL` | prod | todos |
| `POOL_SIZE` | 50 | seed — **nº de usuários no banco** |
| `CONCURRENCY` | 20 | seed (registros em paralelo) |
| `INVITE_TARGET` | kaiangazineu@kaian.com | seed (destino da solicitação de amizade) |
| `VUS_PEAK` | 1000 | stress.js |
| `WS_PEAK` | 1000 | ws-stress.js |
| `WS_MSG_INTERVAL_MS` | 500 | ws-stress.js (cadência por conexão) |
| `WS_HOLD` | 60 | ws-stress.js (segundos que cada socket fica aberto) |

Vazão de mensagens ≈ `WS_PEAK × (1000 / WS_MSG_INTERVAL_MS)` msg/s.

## Como ler o resultado
- `http_req_duration p95/p99` subindo com os VUs → teto de CPU chegando.
- `http_req_failed` saindo de ~0 → o servidor começou a recusar (o threshold marca em >2%).
- `stomp_error_frames > 0` → o broker rejeitou/aplicou backpressure nos SENDs.
- `stomp_connected` estagnando enquanto `WS_PEAK` sobe → teto de conexões WS.

## Notas
- Rode **de uma máquina só sua, contra seu servidor**. É teste de capacidade, não contra terceiros.
- Pool é fixo e reutilizável de propósito — não precisa de cleanup entre execuções. Para apagar: `DELETE /users/{id}` (só a própria conta, com o token dela).
- `.pool.json` contém tokens — está no `.gitignore`, não commite.
