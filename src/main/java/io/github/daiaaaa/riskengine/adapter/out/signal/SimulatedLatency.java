package io.github.daiaaaa.riskengine.adapter.out.signal;

import java.time.Duration;

/**
 * Simulates the network time of a real call (HTTP to a bureau, a database lookup, an ML model).
 */
final class SimulatedLatency {

    private SimulatedLatency() {
    }

    static void sleep(Duration latency) {
        if (latency.isZero() || latency.isNegative()) {
            return;
        }
        try {
            Thread.sleep(latency);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("signal evaluation interrupted", e);
        }
    }
}
