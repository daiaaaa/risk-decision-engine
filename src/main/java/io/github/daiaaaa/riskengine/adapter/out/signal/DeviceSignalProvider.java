package io.github.daiaaaa.riskengine.adapter.out.signal;

import io.github.daiaaaa.riskengine.application.port.SignalProvider;
import io.github.daiaaaa.riskengine.domain.model.RiskSignal;
import io.github.daiaaaa.riskengine.domain.model.Transaction;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

/**
 * Device fingerprint: a transaction from a device never seen for this customer is riskier.
 */
@Component
public class DeviceSignalProvider implements SignalProvider {

    static final String NAME = "device";
    private static final int WEIGHT = 50;

    private static final Map<String, Set<String>> KNOWN_DEVICES = Map.of(
            "cust-001", Set.of("device-abc"),
            "cust-002", Set.of("device-xyz", "device-123"));

    private final Duration latency;

    public DeviceSignalProvider(@Value("${risk.signals.device.latency:50ms}") Duration latency) {
        this.latency = latency;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public RiskSignal evaluate(Transaction transaction) {
        SimulatedLatency.sleep(latency);
        boolean known = KNOWN_DEVICES
                .getOrDefault(transaction.customerId(), Set.of())
                .contains(transaction.deviceId());
        return known
                ? RiskSignal.scored(NAME, 5, WEIGHT, "known device")
                : RiskSignal.scored(NAME, 70, WEIGHT, "device never seen for this customer");
    }
}
