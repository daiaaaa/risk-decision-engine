package io.github.daiaaaa.riskengine.adapter.out.signal;

import io.github.daiaaaa.riskengine.application.port.SignalProvider;
import io.github.daiaaaa.riskengine.domain.model.RiskSignal;
import io.github.daiaaaa.riskengine.domain.model.Transaction;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;

/**
 * Behavioral signal: compares the amount with the customer's usual transaction amount.
 */
@Component
public class CustomerHistorySignalProvider implements SignalProvider {

    static final String NAME = "customer-history";
    private static final int WEIGHT = 50;

    private static final Map<String, BigDecimal> AVERAGE_AMOUNT = Map.of(
            "cust-001", new BigDecimal("500.00"),
            "cust-002", new BigDecimal("2000.00"));

    private static final BigDecimal HIGH_RATIO = BigDecimal.valueOf(5);
    private static final BigDecimal MEDIUM_RATIO = BigDecimal.valueOf(2);

    private final Duration latency;

    public CustomerHistorySignalProvider(@Value("${risk.signals.history.latency:80ms}") Duration latency) {
        this.latency = latency;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public RiskSignal evaluate(Transaction transaction) {
        SimulatedLatency.sleep(latency);
        BigDecimal average = AVERAGE_AMOUNT.get(transaction.customerId());
        if (average == null) {
            return RiskSignal.scored(NAME, 60, WEIGHT, "no transaction history for this customer");
        }
        BigDecimal amount = transaction.amount();
        if (amount.compareTo(average.multiply(HIGH_RATIO)) > 0) {
            return RiskSignal.scored(NAME, 85, WEIGHT, "amount is more than 5x the customer average");
        }
        if (amount.compareTo(average.multiply(MEDIUM_RATIO)) > 0) {
            return RiskSignal.scored(NAME, 45, WEIGHT, "amount is more than 2x the customer average");
        }
        return RiskSignal.scored(NAME, 10, WEIGHT, "amount is within the customer's usual range");
    }
}
