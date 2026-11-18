package io.github.daiaaaa.riskengine.application;

import io.github.daiaaaa.riskengine.application.port.SignalProvider;
import io.github.daiaaaa.riskengine.domain.model.Decision;
import io.github.daiaaaa.riskengine.domain.model.RiskAssessment;
import io.github.daiaaaa.riskengine.domain.model.RiskSignal;
import io.github.daiaaaa.riskengine.domain.model.Transaction;
import io.github.daiaaaa.riskengine.domain.model.TransactionType;
import io.github.daiaaaa.riskengine.domain.policy.DecisionPolicy;
import io.github.daiaaaa.riskengine.domain.policy.RiskScorer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

class RiskAssessmentServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-10T12:00:00Z");

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private final Transaction transaction = new Transaction(
            "tx-1", "cust-001", "device-abc", new BigDecimal("100.00"), TransactionType.PIX);

    @AfterEach
    void tearDown() {
        executor.close();
    }

    @Test
    void combinesSignalsIntoDecision() {
        RiskAssessmentService service = serviceWith(
                fixed("device", RiskSignal.scored("device", 70, 50, "unknown device")),
                fixed("history", RiskSignal.scored("history", 10, 50, "usual amount")));

        RiskAssessment result = service.assess(transaction);

        assertThat(result.score()).isEqualTo(40);
        assertThat(result.decision()).isEqualTo(Decision.REVIEW);
        assertThat(result.signals()).hasSize(2);
        assertThat(result.transactionId()).isEqualTo("tx-1");
        assertThat(result.assessedAt()).isEqualTo(NOW);
    }

    @Test
    void callsProvidersInParallel() {
        Duration delay = Duration.ofMillis(300);
        RiskAssessmentService service = serviceWith(
                slow("a", delay), slow("b", delay), slow("c", delay));

        RiskAssessment result = service.assess(transaction);

        // Sequential would take ~900 ms; parallel takes ~300 ms.
        assertThat(result.processingTime()).isLessThan(Duration.ofMillis(700));
    }

    @Test
    void unavailableSourceIsLeftOutOfTheScoreAndPreventsApproval() {
        RiskAssessmentService service = serviceWith(
                fixed("device", RiskSignal.scored("device", 5, 50, "known device")),
                fixed("history", RiskSignal.unavailable("history", "timeout")));

        RiskAssessment result = service.assess(transaction);

        // Only the device signal counts: score 5, which alone would mean APPROVE.
        assertThat(result.score()).isEqualTo(5);
        assertThat(result.decision()).isEqualTo(Decision.REVIEW);
    }

    private RiskAssessmentService serviceWith(SignalProvider... providers) {
        return new RiskAssessmentService(List.of(providers), new RiskScorer(),
                new DecisionPolicy(40, 75), executor, clock);
    }

    private static SignalProvider fixed(String name, RiskSignal signal) {
        return new SignalProvider() {
            public String name() { return name; }
            public RiskSignal evaluate(Transaction t) { return signal; }
        };
    }

    private static SignalProvider slow(String name, Duration delay) {
        return new SignalProvider() {
            public String name() { return name; }
            public RiskSignal evaluate(Transaction t) {
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return RiskSignal.scored(name, 10, 1, "slow but fine");
            }
        };
    }
}
