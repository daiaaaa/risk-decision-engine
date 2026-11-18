package io.github.daiaaaa.riskengine.domain.policy;

import io.github.daiaaaa.riskengine.domain.model.RiskSignal;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RiskScorerTest {

    private final RiskScorer scorer = new RiskScorer();

    @Test
    void computesWeightedAverage() {
        List<RiskSignal> signals = List.of(
                RiskSignal.scored("a", 80, 75, "x"),
                RiskSignal.scored("b", 20, 25, "y"));

        // (80*75 + 20*25) / 100 = 65
        assertThat(scorer.score(signals)).isEqualTo(65);
    }

    @Test
    void ignoresZeroWeightSignals() {
        List<RiskSignal> signals = List.of(
                RiskSignal.scored("rule", 0, 0, "rule only"),
                RiskSignal.scored("device", 70, 50, "unknown device"));

        assertThat(scorer.score(signals)).isEqualTo(70);
    }

    @Test
    void roundsToNearestInteger() {
        List<RiskSignal> signals = List.of(
                RiskSignal.scored("a", 70, 50, "x"),
                RiskSignal.scored("b", 85, 50, "y"));

        // 77.5 rounds to 78
        assertThat(scorer.score(signals)).isEqualTo(78);
    }

    @Test
    void returnsZeroWhenNoSignalHasWeight() {
        assertThat(scorer.score(List.of(RiskSignal.blocking("deny-list", "blocked")))).isZero();
    }
}
