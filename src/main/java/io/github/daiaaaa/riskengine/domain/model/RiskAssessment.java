package io.github.daiaaaa.riskengine.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * The result of evaluating a transaction: the decision and everything that led to it.
 */
public record RiskAssessment(
        String assessmentId,
        String transactionId,
        Decision decision,
        int score,
        List<RiskSignal> signals,
        Instant assessedAt,
        Duration processingTime) {

    public RiskAssessment {
        signals = List.copyOf(signals);
    }
}
