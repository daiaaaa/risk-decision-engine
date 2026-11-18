# risk-decision-engine

Motor de decisão de risco em tempo real para transações financeiras (PIX, TED, boleto).
Ele recebe uma transação, consulta várias fontes de sinais antifraude **em paralelo**,
combina os sinais em uma pontuação e devolve uma decisão: `APROVAR`, `REVISAR` ou `NEGAR`.

Projeto de estudo construído em fases, que modela o que uma plataforma de orquestração
antifraude e score de risco faz.

## Tecnologias

Java 21 · Spring Boot 4 · Resilience4j · Redis · Apache Kafka · Docker Compose · Maven · JUnit 5 · AssertJ · Testcontainers

## Arquitetura

Arquitetura hexagonal (portas e adaptadores). As dependências sempre apontam para dentro.

```
adaptador/entrada/web  ──►  aplicacao  ──►  dominio
(API REST)                  (orquestrador)  (regras, Java puro)
                                 ▲
adaptador/saida/sinal ───────────┘  (as fontes de sinais implementam a porta)
```

| Pacote | Responsabilidade |
|---|---|
| `dominio.modelo` | `Transacao`, `SinalRisco`, `AvaliacaoRisco`, `Decisao`. Records imutáveis. |
| `dominio.politica` | `CalculadoraRisco` (média ponderada) e `PoliticaDecisao` (limiares e regras bloqueantes). Sem código de framework. |
| `aplicacao` | `ServicoAvaliacaoRisco`: dispara as fontes (fan-out), junta os resultados (fan-in), pontua e decide. `ServicoAvaliacaoIdempotente`: garante a mesma decisão para o mesmo `idTransacao`. |
| `aplicacao.porta` | `ProvedorSinal` (contrato das fontes de sinais) e `RepositorioAvaliacoes` (onde as avaliações ficam guardadas). |
| `adaptador.saida.sinal` | Fontes simuladas: dispositivo, histórico do cliente e lista restritiva. |
| `adaptador.saida.resiliencia` | `ProvedorSinalResiliente`: tempo limite, circuit breaker e fallback em volta de cada fonte. |
| `adaptador.saida.redis` | `RepositorioAvaliacoesRedis`: guarda as avaliações no Redis com validade. |
| `adaptador.saida.kafka` | `PublicadorDecisoesKafka` e o contrato `EventoDecisaoRisco`. |
| `adaptador.saida.memoria` | `FilaRevisaoEmMemoria`: transações aguardando analista (substituída pelo Camunda na fase 6). |
| `adaptador.entrada.kafka` | `ConsumidorDecisoes`: encaminha as decisões `REVISAR` para a revisão manual. |
| `adaptador.entrada.web` | `POST /avaliacoes`, `GET /revisoes`, validação da entrada, montagem da resposta e tratamento de erros. |
| `configuracao` | Conecta as classes sem framework ao Spring. |

### Por que as chamadas são paralelas

Num sistema real, cada fonte de sinais é uma chamada de rede. Chamar uma depois da outra
soma as latências; chamar em paralelo faz a latência total ser a da fonte mais lenta.
As chamadas rodam em virtual threads do Java 21, que tornam barata a espera por I/O.

| Fonte | Latência simulada |
|---|---|
| lista-restritiva | 20 ms |
| dispositivo | 50 ms |
| historico-cliente | 80 ms |
| **Total em sequência** | 150 ms |
| **Total em paralelo** | ~80 ms |

## Regras de decisão

Cada fonte devolve um `SinalRisco` com uma pontuação (0 a 100) e um peso.

```
pontuação final = Σ(pontuação × peso) / Σ(peso)      (arredondada para o inteiro mais próximo)
```

| Pontuação final | Decisão |
|---|---|
| 0 a 39 | `APROVAR` |
| 40 a 74 | `REVISAR` |
| 75 a 100 | `NEGAR` |

Um sinal **bloqueante** (por exemplo, cliente na lista restritiva) força `NEGAR`,
independentemente da pontuação. Fontes bloqueantes têm peso 0, então atuam só como regra.
Os limiares são configuráveis no `application.properties`.

### Fontes simuladas

| Fonte | Peso | Lógica |
|---|---|---|
| `dispositivo` | 50 | Dispositivo conhecido do cliente → 5. Dispositivo desconhecido → 70. |
| `historico-cliente` | 50 | Valor até 2× a média → 10. Acima de 2× → 45. Acima de 5× → 85. Sem histórico → 60. |
| `lista-restritiva` | 0 | Cliente na lista → bloqueante. |

Dados de teste: `cliente-001` (média de R$ 500, dispositivo `dispositivo-abc`),
`cliente-002` (média de R$ 2.000, dispositivos `dispositivo-xyz` e `dispositivo-123`) e
`cliente-bloqueado` (na lista restritiva).

## Resiliência

Cada fonte de sinais é envolvida por um decorator (`ProvedorSinalResiliente`) construído com
os módulos principais do Resilience4j. O orquestrador não sabe disso: ele só enxerga
`ProvedorSinal`s que nunca lançam exceção e nunca travam.

```
fallback ( circuit breaker ( time limiter ( fonte real ) ) )
```

| Camada | O que faz | Configuração |
|---|---|---|
| Time limiter | Desiste da fonte que passa do tempo limite e interrompe a chamada | `tempo-limite=200ms` |
| Circuit breaker | Abre quando 50% ou mais das últimas 10 chamadas falharam (a partir de 5 chamadas); aberto, a fonte é ignorada na hora. Depois de 10 s, 2 chamadas de teste verificam a recuperação | `risco.resiliencia.*` |
| Fallback | Transforma qualquer falha em um sinal **degradado**, em vez de uma exceção | — |

### O que um sinal degradado significa para a decisão

Um sinal degradado tem peso 0, então fica fora da pontuação: o motor não inventa um risco
que não recebeu. Além disso, a política de decisão é **fail-safe**: se algum sinal estiver
degradado, `APROVAR` vira `REVISAR`. Uma transação que não foi totalmente verificada nunca é
aprovada automaticamente, mas também não é recusada: um analista decide. `NEGAR` é mantido,
porque os sinais que responderam já justificam a negação.

O custo dessa escolha é que a fila de revisão cresce durante uma queda. As alternativas eram
*fail-open* (aprovar, com mais risco de fraude) e *fail-closed* (negar, bloqueando clientes
legítimos). A fase 4 adiciona métricas para que um circuito aberto fique visível e gere alerta.

### Simulando uma queda

Rode com a fonte de histórico mais lenta que o tempo limite:

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments=--risco.sinais.historico.latencia=500ms
```

Oito requisições iguais (dispositivo conhecido, valor habitual, que normalmente dariam `APROVAR`):

```
chamadas 1 a 5: REVISAR  degradada=true  ~210 ms | histórico: tempo limite excedido após 200 ms
chamadas 6 a 8: REVISAR  degradada=true   ~60 ms | histórico: circuito aberto: fonte ignorada
```

As cinco primeiras chamadas esperam o tempo limite. Quando a taxa de falha passa de 50%,
o circuito abre e as próximas chamadas pulam a fonte, então a latência cai de ~210 ms para
~60 ms enquanto a fonte se recupera.

## Idempotência

O `idTransacao` funciona como **chave de idempotência**. Se o cliente reenviar a mesma
transação (por exemplo, depois de um timeout de rede), ele recebe a mesma decisão, sem que
a transação seja avaliada de novo.

| Situação | Resposta |
|---|---|
| Primeira vez que o `idTransacao` aparece | Avalia, grava no Redis e responde `200` com `X-Resposta-Repetida: false` |
| Mesmo `idTransacao`, mesmos dados | Devolve a avaliação guardada (mesmo `idAvaliacao`) com `X-Resposta-Repetida: true` |
| Mesmo `idTransacao`, dados diferentes | `409 Conflict`: devolver a decisão guardada seria responder sobre outra transação |

Os "mesmos dados" são comparados por uma impressão digital SHA-256 de cliente, dispositivo,
valor e tipo. `300`, `300.0` e `300.00` contam como o mesmo valor.

### Decisões de projeto

- **Requisições simultâneas:** a gravação usa `SET chave valor NX EX validade`, que é atômico:
  só grava se a chave não existir. As duas requisições avaliam, mas só a primeira grava, e a
  segunda devolve o resultado da primeira. Uma trava distribuída evitaria a avaliação dupla,
  mas a avaliação não tem efeito colateral (só lê sinais), então o custo é só processamento.
- **Redis fora do ar:** o motor continua avaliando, sem idempotência, e registra um aviso no
  log. Parar a antifraude porque o cache caiu seria pior do que, no pior caso, avaliar de novo
  uma transação repetida. Os tempos de conexão e de comando são de 200 ms, para que um Redis
  fora do ar não segure a resposta.
- **Avaliação degradada também é guardada:** se a resposta foi `REVISAR` porque uma fonte caiu,
  o reenvio devolve `REVISAR`, mesmo que a fonte já tenha voltado. O cliente já recebeu essa
  decisão, e ela não pode mudar.
- **Validade de 24 h** (`risco.idempotencia.validade`): o Redis não cresce sem limite, e 24 h
  cobre com folga qualquer reenvio automático.

### Demonstração

```
1ª chamada (R$ 300):  HTTP 200 | repetida=false | idAvaliacao=8d1e0121-... | APROVAR
2ª chamada (R$ 300):  HTTP 200 | repetida=true  | idAvaliacao=8d1e0121-... | APROVAR
3ª chamada (R$ 9999): HTTP 409
TTL da chave no Redis: 86400 s
```

## Eventos (Kafka)

Cada decisão nova é publicada no tópico `decisoes-risco`. Um consumidor encaminha as decisões
`REVISAR` para a fila de revisão manual (`GET /revisoes`).

```
POST /avaliacoes ─► avalia ─► venceu o SET NX? ─► publica ─► decisoes-risco (3 partições, chave = idCliente)
                                                                  │
                                                consumidor (grupo motor-risco-revisao)
                                                                  │
                                    REVISAR? ─► fila de revisão     falhou? ─► 2 novas tentativas ─► decisoes-risco-dlq
```

Exemplo de evento:

```json
{
  "versao": 1, "idAvaliacao": "5f0c...", "idTransacao": "tx-2", "idCliente": "cliente-001",
  "valor": 300.00, "tipo": "PIX", "decisao": "REVISAR", "pontuacao": 40,
  "degradada": false, "avaliadoEm": "2026-11-10T12:00:00Z"
}
```

### Decisões de projeto

- **Só publica quem gravou a avaliação.** Um reenvio (idempotência) não gera evento duplicado.
- **Entrega "pelo menos uma vez".** Se o Redis estiver fora do ar, um reenvio pode publicar de
  novo; por isso o consumidor também é idempotente e ignora um `idAvaliacao` que já recebeu.
- **Chave = `idCliente`.** As decisões de um cliente caem na mesma partição e são consumidas na
  ordem em que foram publicadas.
- **Produtor confiável:** `acks=all` e `enable.idempotence=true`.
- **A publicação não atrasa a API.** O envio roda em outra virtual thread, com tempos curtos
  (`max.block.ms=2000`), para que um Kafka fora do ar não segure a resposta.
- **DLQ:** uma mensagem que falha tem mais 2 tentativas, com 1 s de intervalo, e depois vai para
  `decisoes-risco-dlq`, com o motivo da falha nos cabeçalhos. Mensagem inválida (JSON quebrado,
  campo obrigatório ausente, versão desconhecida) vai direto, porque tentar de novo não resolve.
- **Contrato versionado.** O campo `versao` permite evoluir o evento sem quebrar quem já consome.
- **O consumidor mora na mesma aplicação** para manter o projeto simples. Num sistema real, ele
  estaria no serviço de revisão; aqui, os dois lados só conversam pelo tópico.

### Limitação conhecida: perda de evento se o Kafka cair

A decisão é devolvida ao cliente mesmo que a publicação falhe; nesse caso, o evento fica só no
log. A solução de mercado é o **padrão Transactional Outbox**: gravar o evento em uma tabela, na
mesma transação de banco que registra a decisão, e um processo separado publica no Kafka depois,
com novas tentativas. Não foi implementado aqui porque exigiria um banco de dados só para isso.

### Demonstração

```
POST /avaliacoes:
  tx-aprovada -> APROVAR
  tx-revisar  -> REVISAR
  tx-revisar  -> REVISAR  (reenvio)
GET /revisoes: 1 pendente(s)
  tx-revisar pontuacao=40 degradada=false
Mensagens por partição: decisoes-risco:0:0  decisoes-risco:1:2  decisoes-risco:2:0
DLQ decisoes-risco-dlq: valor='mensagem invalida'
```

As duas decisões do mesmo cliente foram para a mesma partição. O reenvio não gerou uma terceira
mensagem. A mensagem inválida, enviada direto no tópico, terminou na DLQ.

## Como rodar

Requisitos: Java 21 e Docker. O Maven é baixado pelo wrapper.

```bash
docker compose up -d          # sobe o Redis e o Kafka
./mvnw spring-boot:run        # Windows: mvnw.cmd spring-boot:run
docker compose down           # derruba os contêineres
```

A aplicação também sobe sem o Redis e sem o Kafka; nesse caso, funciona sem idempotência e
sem eventos.

### Testes

```bash
./mvnw test                   # Windows: mvnw.cmd test
```

Os testes de idempotência e de eventos sobem um Redis e um Kafka reais em Docker com
Testcontainers, então o Docker precisa estar rodando.

## Exemplos

Dispositivo conhecido, valor habitual → `APROVAR` (pontuação 8):

```bash
curl -s -X POST localhost:8080/avaliacoes -H "Content-Type: application/json" -d '{
  "idTransacao": "tx-1", "idCliente": "cliente-001",
  "idDispositivo": "dispositivo-abc", "valor": 300.00, "tipo": "PIX"
}'
```

Dispositivo novo → `REVISAR` (pontuação 40):

```bash
curl -s -X POST localhost:8080/avaliacoes -H "Content-Type: application/json" -d '{
  "idTransacao": "tx-2", "idCliente": "cliente-001",
  "idDispositivo": "dispositivo-novo", "valor": 300.00, "tipo": "PIX"
}'
```

Dispositivo novo e valor acima de 5× a média → `NEGAR` (pontuação 78):

```bash
curl -s -X POST localhost:8080/avaliacoes -H "Content-Type: application/json" -d '{
  "idTransacao": "tx-3", "idCliente": "cliente-001",
  "idDispositivo": "dispositivo-novo", "valor": 3000.00, "tipo": "PIX"
}'
```

Exemplo de resposta:

```json
{
  "idAvaliacao": "5f0c...",
  "idTransacao": "tx-1",
  "decisao": "APROVAR",
  "pontuacao": 8,
  "degradada": false,
  "sinais": [
    { "fonte": "lista-restritiva", "pontuacao": 0, "peso": 0, "bloqueante": false, "degradado": false, "motivo": "cliente não está na lista restritiva" },
    { "fonte": "dispositivo", "pontuacao": 5, "peso": 50, "bloqueante": false, "degradado": false, "motivo": "dispositivo conhecido" },
    { "fonte": "historico-cliente", "pontuacao": 10, "peso": 50, "bloqueante": false, "degradado": false, "motivo": "valor dentro do padrão do cliente" }
  ],
  "avaliadoEm": "2026-11-10T12:00:00Z",
  "tempoProcessamentoMs": 81
}
```

Uma entrada inválida devolve `400` no formato Problem Details (RFC 9457), com título
"Requisição inválida". Reaproveitar um `idTransacao` com outros dados devolve `409`, com
título "Conflito de idempotência".

## Roteiro

- [x] **1. Núcleo**: API REST, fontes de sinais em paralelo, pontuação, política de decisão e testes
- [x] **2a. Resiliência**: tempo limite, circuit breaker e fallback (Resilience4j), decisão fail-safe
- [x] **2b. Idempotência**: o mesmo `idTransacao` devolve a mesma decisão, com Redis
- [x] **3. Eventos**: publicação das decisões no Kafka, consumidor e tópico de mensagens mortas (DLQ)
- [ ] **4. Observabilidade**: métricas de latência por fonte (Micrometer), Prometheus e Grafana
- [ ] **5. Segurança**: OAuth2 / JWT com Keycloak
- [ ] **6. Camunda 8**: processo BPMN de revisão manual para as decisões `REVISAR`
