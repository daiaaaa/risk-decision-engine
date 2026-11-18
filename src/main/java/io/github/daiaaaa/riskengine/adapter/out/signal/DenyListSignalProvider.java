package io.github.daiaaaa.riskengine.adapter.out.signal;

import io.github.daiaaaa.riskengine.application.port.SignalProvider;
import io.github.daiaaaa.riskengine.domain.model.RiskSignal;
import io.github.daiaaaa.riskengine.domain.model.Transaction;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;

/**
 * Hard rule: customers on the deny list are always refused.
 * Weight is 0 because this source does not contribute to the score; it blocks.
 */
@Component
public class DenyListSignalProvider implements SignalProvider {

    static final String NAME = "deny-list";

    private static final Set<String> DENIED_CUSTOMERS = Set.of("cust-blocked");

    private final Duration latency;

    public DenyListSignalProvider(@Value("${risk.signals.deny-list.latency:20ms}") Duration latency) {
        this.latency = latency;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public RiskSignal evaluate(Transaction transaction) {
        SimulatedLatency.sleep(latency);
        if (DENIED_CUSTOMERS.contains(transaction.customerId())) {
            return RiskSignal.blocking(NAME, "customer is on the deny list");
        }
        return RiskSignal.scored(NAME, 0, 0, "customer is not on the deny list");
    }
}
