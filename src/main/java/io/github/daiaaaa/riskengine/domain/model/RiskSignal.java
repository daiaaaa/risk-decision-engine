package io.github.daiaaaa.riskengine.domain.model;

import java.util.Objects;

/**
 * The opinion of one signal source about a transaction.
 *
 * @param source   name of the source that produced the signal
 * @param score    risk from 0 (no risk) to 100 (maximum risk)
 * @param weight   how much this signal counts in the final score; 0 means it only acts as a rule
 * @param blocking when true, the transaction is denied regardless of the final score
 * @param reason   human-readable explanation, useful for analysts and audits
 */
public record RiskSignal(String source, int score, int weight, boolean blocking, String reason) {

    public RiskSignal {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(reason, "reason");
        if (score < 0 || score > 100) {
            throw new IllegalArgumentException("score must be between 0 and 100");
        }
        if (weight < 0) {
            throw new IllegalArgumentException("weight must not be negative");
        }
    }

    public static RiskSignal scored(String source, int score, int weight, String reason) {
        return new RiskSignal(source, score, weight, false, reason);
    }

    public static RiskSignal blocking(String source, String reason) {
        return new RiskSignal(source, 100, 0, true, reason);
    }
}
