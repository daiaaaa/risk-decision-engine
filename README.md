# risk-decision-engine

Real-time risk decision engine for financial transactions (PIX, TED, boleto).
It receives a transaction, queries several antifraud signal sources **in parallel**,
combines them into a score and returns a decision: `APPROVE`, `REVIEW` or `DENY`.

A study project built in phases, modelling what an antifraud orchestration and
risk-scoring platform does.

## Stack

Java 21 · Spring Boot 4 · Resilience4j · Maven · JUnit 5 · AssertJ

## Architecture

Hexagonal architecture (ports and adapters). Dependencies always point inwards.

```
adapter/in/web  ──►  application  ──►  domain
(REST API)           (orchestrator)    (rules, plain Java)
                          ▲
adapter/out/signal ───────┘  (signal sources implement the port)
```

| Package | Responsibility |
|---|---|
| `domain.model` | `Transaction`, `RiskSignal`, `RiskAssessment`, `Decision`. Immutable records. |
| `domain.policy` | `RiskScorer` (weighted average) and `DecisionPolicy` (thresholds and blocking rules). No framework code. |
| `application` | `RiskAssessmentService`: fan-out to every provider, fan-in, score, decide. |
| `application.port` | `SignalProvider`: the contract every signal source implements. |
| `adapter.out.signal` | Simulated sources: device, customer history, deny list. |
| `adapter.out.resilience` | `ResilientSignalProvider`: timeout, circuit breaker and fallback around each source. |
| `adapter.in.web` | `POST /assessments`, input validation, response mapping. |
| `config` | Wires the framework-free classes into Spring. |

### Why parallel calls

Each signal source is a network call in a real system. Calling them one after
another adds up the latencies; calling them in parallel makes the total latency
equal to the slowest source. Calls run on Java 21 virtual threads, which keep
waiting on I/O cheap.

| Source | Simulated latency |
|---|---|
| deny-list | 20 ms |
| device | 50 ms |
| customer-history | 80 ms |
| **Sequential total** | 150 ms |
| **Parallel total** | ~80 ms |

## Decision rules

Each source returns a `RiskSignal` with a score (0–100) and a weight.

```
final score = Σ(score × weight) / Σ(weight)      (rounded to the nearest integer)
```

| Final score | Decision |
|---|---|
| 0–39 | `APPROVE` |
| 40–74 | `REVIEW` |
| 75–100 | `DENY` |

A **blocking** signal (for example, a customer on the deny list) forces `DENY`
regardless of the score. Blocking sources have weight 0, so they act only as rules.
Thresholds are configurable in `application.properties`.

### Simulated sources

| Source | Weight | Logic |
|---|---|---|
| `device` | 50 | Known device for the customer → 5. Unknown device → 70. |
| `customer-history` | 50 | Amount ≤ 2× the average → 10. > 2× → 45. > 5× → 85. No history → 60. |
| `deny-list` | 0 | Customer on the list → blocking. |

Test data: `cust-001` (average R$ 500, device `device-abc`), `cust-002`
(average R$ 2,000, devices `device-xyz` and `device-123`), `cust-blocked` (deny list).

## Running

Requirements: Java 21. Maven is downloaded by the wrapper.

```bash
./mvnw spring-boot:run        # Windows: mvnw.cmd spring-boot:run
./mvnw test                   # Windows: mvnw.cmd test
```

## Examples

Known device, usual amount → `APPROVE` (score 8):

```bash
curl -s -X POST localhost:8080/assessments -H "Content-Type: application/json" -d '{
  "transactionId": "tx-1", "customerId": "cust-001",
  "deviceId": "device-abc", "amount": 300.00, "type": "PIX"
}'
```

Unknown device → `REVIEW` (score 40):

```bash
curl -s -X POST localhost:8080/assessments -H "Content-Type: application/json" -d '{
  "transactionId": "tx-2", "customerId": "cust-001",
  "deviceId": "device-new", "amount": 300.00, "type": "PIX"
}'
```

Unknown device and amount above 5× the average → `DENY` (score 78):

```bash
curl -s -X POST localhost:8080/assessments -H "Content-Type: application/json" -d '{
  "transactionId": "tx-3", "customerId": "cust-001",
  "deviceId": "device-new", "amount": 3000.00, "type": "PIX"
}'
```

Sample response:

```json
{
  "assessmentId": "5f0c...",
  "transactionId": "tx-1",
  "decision": "APPROVE",
  "score": 8,
  "degraded": false,
  "signals": [
    { "source": "deny-list", "score": 0, "weight": 0, "blocking": false, "degraded": false, "reason": "customer is not on the deny list" },
    { "source": "device", "score": 5, "weight": 50, "blocking": false, "degraded": false, "reason": "known device" },
    { "source": "customer-history", "score": 10, "weight": 50, "blocking": false, "degraded": false, "reason": "amount is within the customer's usual range" }
  ],
  "assessedAt": "2026-11-10T12:00:00Z",
  "processingTimeMs": 81
}
```

Invalid input returns `400` as RFC 9457 Problem Details.

## Resilience

Every signal source is wrapped in a decorator (`ResilientSignalProvider`) built with
the Resilience4j core modules. The orchestrator does not know about it; it only sees
`SignalProvider`s that never throw and never hang.

```
fallback ( circuit breaker ( time limiter ( real source ) ) )
```

| Layer | What it does | Setting |
|---|---|---|
| Time limiter | Gives up on a source slower than the budget and interrupts the call | `timeout=200ms` |
| Circuit breaker | Opens when ≥ 50% of the last 10 calls failed (after 5 calls); while open, the source is skipped instantly. After 10 s, 2 trial calls test recovery | `risk.resilience.*` |
| Fallback | Turns any failure into a **degraded** signal instead of an exception | — |

### What a degraded signal means for the decision

A degraded signal has weight 0, so it is left out of the score: the engine does not
invent a risk value it never received. On top of that, the decision policy is
**fail-safe**: if any signal is degraded, `APPROVE` becomes `REVIEW`. A transaction that
was not fully checked is never auto-approved, but it is not refused either; an analyst
decides. `DENY` is kept, since the remaining signals already justify it.

The trade-off: during an outage the review queue grows. The alternatives were
*fail-open* (approve, more fraud risk) and *fail-closed* (deny, legitimate customers
blocked). Phase 4 adds metrics so an open circuit is visible and can be alerted on.

### Simulating an outage

Run with the history source slower than the timeout:

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments=--risk.signals.history.latency=500ms
```

Eight identical requests (known device, usual amount, normally `APPROVE`):

```
call 1-5: REVIEW  degraded=true  ~210 ms | history: timeout after 200 ms
call 6-8: REVIEW  degraded=true   ~60 ms | history: circuit open: source skipped
```

The first five calls wait for the timeout. Once the failure rate crosses 50%, the
circuit opens and the next calls skip the source, so latency drops from ~210 ms to
~60 ms while the source recovers.

## Roadmap

- [x] **1. Core** — REST API, parallel signal providers, scoring and decision policy, tests
- [x] **2a. Resilience** — timeouts, circuit breaker and fallback (Resilience4j), fail-safe decision
- [ ] **2b. Idempotency** — same `transactionId` returns the same decision, with Redis
- [ ] **3. Events** — publish decisions to Kafka, consumer and dead-letter topic
- [ ] **4. Observability** — latency metrics per source (Micrometer), Prometheus and Grafana
- [ ] **5. Security** — OAuth2 / JWT with Keycloak
- [ ] **6. Camunda 8** — BPMN manual-review process for `REVIEW` decisions
