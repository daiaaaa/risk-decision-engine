package io.github.daiaaaa.riskengine.config;

import io.github.daiaaaa.riskengine.adapter.out.resilience.ResilientSignalProvider;
import io.github.daiaaaa.riskengine.application.RiskAssessmentService;
import io.github.daiaaaa.riskengine.application.port.SignalProvider;
import io.github.daiaaaa.riskengine.domain.policy.DecisionPolicy;
import io.github.daiaaaa.riskengine.domain.policy.RiskScorer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Wires the framework-free domain and application classes into Spring.
 * This is the only place that knows how the pieces fit together.
 */
@Configuration
@EnableConfigurationProperties(ResilienceProperties.class)
public class RiskEngineConfig {

    /**
     * One virtual thread per provider call (Java 21). Calls spend most of their time
     * waiting on I/O, and virtual threads make that waiting cheap.
     */
    @Bean(destroyMethod = "close")
    public ExecutorService signalExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public RiskScorer riskScorer() {
        return new RiskScorer();
    }

    @Bean
    public DecisionPolicy decisionPolicy(
            @Value("${risk.decision.review-threshold}") int reviewThreshold,
            @Value("${risk.decision.deny-threshold}") int denyThreshold) {
        return new DecisionPolicy(reviewThreshold, denyThreshold);
    }

    /**
     * One circuit breaker per source, all sharing the same settings. Keeping them in a
     * registry lets phase 4 export their state as metrics.
     */
    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry(ResilienceProperties props) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(props.slidingWindowSize())
                .minimumNumberOfCalls(props.minimumNumberOfCalls())
                .failureRateThreshold(props.failureRateThreshold())
                .waitDurationInOpenState(props.waitDurationInOpenState())
                .permittedNumberOfCallsInHalfOpenState(props.permittedCallsInHalfOpenState())
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .build();
        return CircuitBreakerRegistry.of(config);
    }

    /**
     * The raw providers are Spring components. Each one is wrapped in the resilience
     * decorator here, so the orchestrator only ever sees the protected versions.
     */
    @Bean
    public RiskAssessmentService riskAssessmentService(List<SignalProvider> rawProviders,
                                                       CircuitBreakerRegistry registry,
                                                       ResilienceProperties props,
                                                       RiskScorer scorer,
                                                       DecisionPolicy policy,
                                                       ExecutorService signalExecutor,
                                                       Clock clock) {
        TimeLimiterConfig timeLimiterConfig = TimeLimiterConfig.custom()
                .timeoutDuration(props.timeout())
                .cancelRunningFuture(true)
                .build();
        List<SignalProvider> protectedProviders = rawProviders.stream()
                .<SignalProvider>map(provider -> {
                    CircuitBreaker circuitBreaker = registry.circuitBreaker(provider.name());
                    TimeLimiter timeLimiter = TimeLimiter.of(provider.name(), timeLimiterConfig);
                    return new ResilientSignalProvider(provider, circuitBreaker, timeLimiter, signalExecutor);
                })
                .toList();
        return new RiskAssessmentService(protectedProviders, scorer, policy, signalExecutor, clock);
    }
}
