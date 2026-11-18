package io.github.daiaaaa.riskengine.config;

import io.github.daiaaaa.riskengine.application.RiskAssessmentService;
import io.github.daiaaaa.riskengine.application.port.SignalProvider;
import io.github.daiaaaa.riskengine.domain.policy.DecisionPolicy;
import io.github.daiaaaa.riskengine.domain.policy.RiskScorer;
import org.springframework.beans.factory.annotation.Value;
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

    @Bean
    public RiskAssessmentService riskAssessmentService(List<SignalProvider> providers,
                                                       RiskScorer scorer,
                                                       DecisionPolicy policy,
                                                       ExecutorService signalExecutor,
                                                       Clock clock) {
        return new RiskAssessmentService(providers, scorer, policy, signalExecutor, clock);
    }
}
