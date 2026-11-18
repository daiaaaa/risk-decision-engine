package io.github.daiaaaa.riskengine.application;

import io.github.daiaaaa.riskengine.application.port.SignalProvider;
import io.github.daiaaaa.riskengine.domain.model.Decision;
import io.github.daiaaaa.riskengine.domain.model.RiskAssessment;
import io.github.daiaaaa.riskengine.domain.model.RiskSignal;
import io.github.daiaaaa.riskengine.domain.model.Transaction;
import io.github.daiaaaa.riskengine.domain.policy.DecisionPolicy;
import io.github.daiaaaa.riskengine.domain.policy.RiskScorer;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Orchestrates a risk assessment: fan-out to every signal provider in parallel,
 * fan-in the results, score them and apply the decision policy.
 *
 * <p>This class assumes providers do not throw or hang. That guarantee comes from
 * wrapping each provider in a resilience decorator (timeout, circuit breaker, fallback)
 * when the application is wired; the orchestrator itself stays simple.
 */
public class RiskAssessmentService {

    private final List<SignalProvider> providers;
    private final RiskScorer scorer;
    private final DecisionPolicy policy;
    private final Executor executor;
    private final Clock clock;

    public RiskAssessmentService(List<SignalProvider> providers,
                                 RiskScorer scorer,
                                 DecisionPolicy policy,
                                 Executor executor,
                                 Clock clock) {
        if (providers.isEmpty()) {
            throw new IllegalArgumentException("at least one SignalProvider is required");
        }
        this.providers = List.copyOf(providers);
        this.scorer = scorer;
        this.policy = policy;
        this.executor = executor;
        this.clock = clock;
    }

    public RiskAssessment assess(Transaction transaction) {
        long start = System.nanoTime();

        // Fan-out: start every provider at the same time.
        List<CompletableFuture<RiskSignal>> futures = providers.stream()
                .map(provider -> CompletableFuture.supplyAsync(
                        () -> provider.evaluate(transaction), executor))
                .toList();

        // Fan-in: total latency is the slowest provider, not the sum of all of them.
        List<RiskSignal> signals = futures.stream()
                .map(CompletableFuture::join)
                .toList();

        int score = scorer.score(signals);
        Decision decision = policy.decide(score, signals);

        return new RiskAssessment(
                UUID.randomUUID().toString(),
                transaction.transactionId(),
                decision,
                score,
                signals,
                Instant.now(clock),
                Duration.ofNanos(System.nanoTime() - start));
    }
}
