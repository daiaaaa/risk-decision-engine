package io.github.daiaaaa.riskengine.adapter.out.resilience;

import io.github.daiaaaa.riskengine.application.port.SignalProvider;
import io.github.daiaaaa.riskengine.domain.model.RiskSignal;
import io.github.daiaaaa.riskengine.domain.model.Transaction;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.timelimiter.TimeLimiter;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeoutException;

/**
 * Decorator that protects one signal source. Order of the layers, from outside in:
 *
 * <pre>
 *   fallback  ( circuit breaker ( time limiter ( real provider ) ) )
 * </pre>
 *
 * <ul>
 *   <li><b>Time limiter</b>: gives up on a call that takes longer than the budget.</li>
 *   <li><b>Circuit breaker</b>: counts failures and timeouts; when too many happen, it opens
 *       and rejects calls immediately, so a struggling source gets time to recover and we stop
 *       wasting the latency budget on it.</li>
 *   <li><b>Fallback</b>: any failure becomes a degraded signal instead of an exception, so one
 *       source can never break the whole assessment.</li>
 * </ul>
 */
public class ResilientSignalProvider implements SignalProvider {

    private final SignalProvider delegate;
    private final CircuitBreaker circuitBreaker;
    private final TimeLimiter timeLimiter;
    private final ExecutorService executor;

    public ResilientSignalProvider(SignalProvider delegate,
                                   CircuitBreaker circuitBreaker,
                                   TimeLimiter timeLimiter,
                                   ExecutorService executor) {
        this.delegate = delegate;
        this.circuitBreaker = circuitBreaker;
        this.timeLimiter = timeLimiter;
        this.executor = executor;
    }

    @Override
    public String name() {
        return delegate.name();
    }

    @Override
    public RiskSignal evaluate(Transaction transaction) {
        try {
            return circuitBreaker.executeCallable(() ->
                    timeLimiter.executeFutureSupplier(() ->
                            executor.submit(() -> delegate.evaluate(transaction))));
        } catch (CallNotPermittedException e) {
            return RiskSignal.unavailable(name(), "circuit open: source skipped");
        } catch (TimeoutException e) {
            return RiskSignal.unavailable(name(), "timeout after " + timeLimiter.getTimeLimiterConfig()
                    .getTimeoutDuration().toMillis() + " ms");
        } catch (Exception e) {
            return RiskSignal.unavailable(name(), "source error: " + e.getClass().getSimpleName());
        }
    }

    public CircuitBreaker.State circuitState() {
        return circuitBreaker.getState();
    }
}
