# risk-decision-engine

Motor de decisão de risco em tempo real para transações financeiras (PIX, TED, boleto).
Ele recebe uma transação, consulta várias fontes de sinais antifraude **em paralelo**,
combina os sinais em uma pontuação e devolve uma decisão: `APROVAR`, `REVISAR` ou `NEGAR`.

Projeto de estudo construído em fases, que modela o que uma plataforma de orquestração
antifraude e score de risco faz.

## Tecnologias

Java 21 · Spring Boot 4 · Resilience4j · Maven · JUnit 5 · AssertJ

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
| `aplicacao` | `ServicoAvaliacaoRisco`: dispara as fontes (fan-out), junta os resultados (fan-in), pontua e decide. |
| `aplicacao.porta` | `ProvedorSinal`: o contrato que toda fonte de sinais implementa. |
| `adaptador.saida.sinal` | Fontes simuladas: dispositivo, histórico do cliente e lista restritiva. |
| `adaptador.saida.resiliencia` | `ProvedorSinalResiliente`: tempo limite, circuit breaker e fallback em volta de cada fonte. |
| `adaptador.entrada.web` | `POST /avaliacoes`, validação da entrada e montagem da resposta. |
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

## Como rodar

Requisito: Java 21. O Maven é baixado pelo wrapper.

```bash
./mvnw spring-boot:run        # Windows: mvnw.cmd spring-boot:run
./mvnw test                   # Windows: mvnw.cmd test
```

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
"Requisição inválida".

## Roteiro

- [x] **1. Núcleo**: API REST, fontes de sinais em paralelo, pontuação, política de decisão e testes
- [x] **2a. Resiliência**: tempo limite, circuit breaker e fallback (Resilience4j), decisão fail-safe
- [ ] **2b. Idempotência**: o mesmo `idTransacao` devolve a mesma decisão, com Redis
- [ ] **3. Eventos**: publicação das decisões no Kafka, consumidor e tópico de mensagens mortas (DLQ)
- [ ] **4. Observabilidade**: métricas de latência por fonte (Micrometer), Prometheus e Grafana
- [ ] **5. Segurança**: OAuth2 / JWT com Keycloak
- [ ] **6. Camunda 8**: processo BPMN de revisão manual para as decisões `REVISAR`
