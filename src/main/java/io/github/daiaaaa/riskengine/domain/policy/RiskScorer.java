package io.github.daiaaaa.riskengine.domain.policy;

import io.github.daiaaaa.riskengine.domain.model.RiskSignal;

import java.util.List;

/**
 * Combines the signals into a single score using a weighted average.
 * Signals with weight 0 are ignored here; they act only as rules (see {@link DecisionPolicy}).
 */
public class RiskScorer {

    public int score(List<RiskSignal> signals) {
        int totalWeight = signals.stream().mapToInt(RiskSignal::weight).sum();
        if (totalWeight == 0) {
            return 0;
        }
        int weightedSum = signals.stream().mapToInt(s -> s.score() * s.weight()).sum();
        // Integer rounding to the nearest value, avoiding floating point.
        return (weightedSum + totalWeight / 2) / totalWeight;
    }
}
