package io.github.daiaaaa.riskengine.adapter.out.resilience;

import io.github.daiaaaa.riskengine.application.port.SignalProvider;
import io.github.daiaaaa.riskengine.domain.model.RiskSignal;
import io.github.daiaaaa.riskengine.domain.model.Transaction;
import io.github.daiaaaa.riskengine.domain.model.TransactionType;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ResilientSignalProviderTest {

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    private final Transaction transaction = new Transaction(
            "tx-1", "cust-001", "device-abc", new BigDecimal("100.00"), TransactionType.PIX);

    @AfterEach
    void tearDown() {
        executor.close();
    }

    @Test
    void healthySourcePassesThrough() {
        RiskSignal expected = RiskSignal.scored("device", 5, 50, "known device");
        ResilientSignalProvider provider = resilient(fixed(expected), Duration.ofSeconds(1), Duration.ofSeconds(60));

        assertThat(provider.evaluate(transaction)).isEqualTo(expected);
    }

    @Test
    void slowSourceTimesOutAndIsCancelled() throws InterruptedException {
        CountDownLatch interrupted = new CountDownLatch(1);
        SignalProvider slow = provider(t -> {
            try {
                Thread.sleep(Duration.ofSeconds(5));
            } catch (InterruptedException e) {
                interrupted.countDown();
                Thread.currentThread().interrupt();
            }
            return RiskSignal.scored("device", 5, 50, "too late");
        });
        ResilientSignalProvider provider = resilient(slow, Duration.ofMillis(100), Duration.ofSeconds(60));

        long start = System.nanoTime();
        RiskSignal signal = provider.evaluate(transaction);
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

        assertThat(signal.degraded()).isTrue();
        assertThat(signal.weight()).isZero();
        assertThat(signal.reason()).startsWith("timeout");
        assertThat(elapsed).isLessThan(Duration.ofMillis(600));
        // The abandoned call is interrupted, so it does not keep a thread busy.
        assertThat(interrupted.await(1, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void failingSourceBecomesDegradedSignal() {
        SignalProvider broken = provider(t -> {
            throw new IllegalStateException("bureau down");
        });
        ResilientSignalProvider provider = resilient(broken, Duration.ofSeconds(1), Duration.ofSeconds(60));

        RiskSignal signal = provider.evaluate(transaction);

        assertThat(signal.degraded()).isTrue();
        assertThat(signal.reason()).isEqualTo("source error: IllegalStateException");
    }

    @Test
    void circuitOpensAfterRepeatedFailuresAndStopsCallingTheSource() {
        AtomicInteger calls = new AtomicInteger();
        SignalProvider broken = provider(t -> {
            calls.incrementAndGet();
            throw new IllegalStateException("bureau down");
        });
        ResilientSignalProvider provider = resilient(broken, Duration.ofSeconds(1), Duration.ofSeconds(60));

        for (int i = 0; i < 4; i++) {
            provider.evaluate(transaction);
        }
        assertThat(provider.circuitState()).isEqualTo(CircuitBreaker.State.OPEN);

        RiskSignal signal = provider.evaluate(transaction);

        assertThat(signal.reason()).isEqualTo("circuit open: source skipped");
        assertThat(calls).hasValue(4);
    }

    @Test
    void circuitClosesAgainWhenTheSourceRecovers() throws InterruptedException {
        AtomicBoolean healthy = new AtomicBoolean(false);
        SignalProvider flaky = provider(t -> {
            if (!healthy.get()) {
                throw new IllegalStateException("bureau down");
            }
            return RiskSignal.scored("device", 5, 50, "known device");
        });
        ResilientSignalProvider provider = resilient(flaky, Duration.ofSeconds(1), Duration.ofMillis(100));

        for (int i = 0; i < 4; i++) {
            provider.evaluate(transaction);
        }
        assertThat(provider.circuitState()).isEqualTo(CircuitBreaker.State.OPEN);

        healthy.set(true);
        Thread.sleep(300); // wait longer than waitDurationInOpenState -> HALF_OPEN

        assertThat(provider.circuitState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);
        provider.evaluate(transaction);
        provider.evaluate(transaction);
        assertThat(provider.circuitState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    private ResilientSignalProvider resilient(SignalProvider delegate, Duration timeout, Duration openWait) {
        CircuitBreakerConfig cbConfig = CircuitBreakerConfig.custom()
                .slidingWindowSize(4)
                .minimumNumberOfCalls(4)
                .failureRateThreshold(50)
                .waitDurationInOpenState(openWait)
                .permittedNumberOfCallsInHalfOpenState(2)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .build();
        TimeLimiterConfig tlConfig = TimeLimiterConfig.custom()
                .timeoutDuration(timeout)
                .cancelRunningFuture(true)
                .build();
        return new ResilientSignalProvider(delegate,
                CircuitBreaker.of("test", cbConfig), TimeLimiter.of("test", tlConfig), executor);
    }

    private interface Evaluation {
        RiskSignal apply(Transaction transaction);
    }

    private static SignalProvider provider(Evaluation evaluation) {
        return new SignalProvider() {
            public String name() { return "device"; }
            public RiskSignal evaluate(Transaction t) { return evaluation.apply(t); }
        };
    }

    private static SignalProvider fixed(RiskSignal signal) {
        return provider(t -> signal);
    }
}
