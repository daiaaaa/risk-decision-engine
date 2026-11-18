package io.github.daiaaaa.riskengine.domain.policy;

import io.github.daiaaaa.riskengine.domain.model.Decision;
import io.github.daiaaaa.riskengine.domain.model.RiskSignal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class DecisionPolicyTest {

    private final DecisionPolicy policy = new DecisionPolicy(40, 75);

    @ParameterizedTest(name = "score {0} -> {1}")
    @CsvSource({
            "0,   APPROVE",
            "39,  APPROVE",
            "40,  REVIEW",
            "74,  REVIEW",
            "75,  DENY",
            "100, DENY"
    })
    void appliesThresholds(int score, Decision expected) {
        assertThat(policy.decide(score, List.of())).isEqualTo(expected);
    }

    @Test
    void blockingSignalDeniesEvenWithLowScore() {
        List<RiskSignal> signals = List.of(RiskSignal.blocking("deny-list", "blocked"));

        assertThat(policy.decide(0, signals)).isEqualTo(Decision.DENY);
    }

    @Test
    void rejectsInvalidThresholds() {
        assertThatIllegalArgumentException().isThrownBy(() -> new DecisionPolicy(80, 40));
        assertThatIllegalArgumentException().isThrownBy(() -> new DecisionPolicy(-1, 40));
        assertThatIllegalArgumentException().isThrownBy(() -> new DecisionPolicy(40, 101));
    }
}
