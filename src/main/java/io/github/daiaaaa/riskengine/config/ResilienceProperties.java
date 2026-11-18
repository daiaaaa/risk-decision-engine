package io.github.daiaaaa.riskengine.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Resilience settings applied to every signal source.
 *
 * @param timeout                      maximum time a single source may take
 * @param slidingWindowSize            number of recent calls the circuit breaker looks at
 * @param minimumNumberOfCalls         calls needed before the failure rate is evaluated
 * @param failureRateThreshold         failure percentage that opens the circuit
 * @param waitDurationInOpenState      how long the circuit stays open before testing the source again
 * @param permittedCallsInHalfOpenState trial calls allowed while testing a recovering source
 */
@ConfigurationProperties(prefix = "risk.resilience")
public record ResilienceProperties(
        Duration timeout,
        int slidingWindowSize,
        int minimumNumberOfCalls,
        float failureRateThreshold,
        Duration waitDurationInOpenState,
        int permittedCallsInHalfOpenState) {
}
