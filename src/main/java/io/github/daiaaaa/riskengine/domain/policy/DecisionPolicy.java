package io.github.daiaaaa.riskengine.domain.policy;

import io.github.daiaaaa.riskengine.domain.model.Decision;
import io.github.daiaaaa.riskengine.domain.model.RiskSignal;

import java.util.List;

/**
 * Turns a score into a decision. Blocking rules win over the score.
 *
 * <pre>
 *   score &lt; reviewThreshold                  -&gt; APPROVE
 *   reviewThreshold &lt;= score &lt; denyThreshold -&gt; REVIEW
 *   score &gt;= denyThreshold                    -&gt; DENY
 * </pre>
 */
public class DecisionPolicy {

    private final int reviewThreshold;
    private final int denyThreshold;

    public DecisionPolicy(int reviewThreshold, int denyThreshold) {
        if (reviewThreshold < 0 || denyThreshold > 100 || reviewThreshold >= denyThreshold) {
            throw new IllegalArgumentException(
                    "expected 0 <= reviewThreshold < denyThreshold <= 100");
        }
        this.reviewThreshold = reviewThreshold;
        this.denyThreshold = denyThreshold;
    }

    public Decision decide(int score, List<RiskSignal> signals) {
        if (signals.stream().anyMatch(RiskSignal::blocking)) {
            return Decision.DENY;
        }
        if (score >= denyThreshold) {
            return Decision.DENY;
        }
        if (score >= reviewThreshold) {
            return Decision.REVIEW;
        }
        return Decision.APPROVE;
    }
}
