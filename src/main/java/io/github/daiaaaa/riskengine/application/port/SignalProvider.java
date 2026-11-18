package io.github.daiaaaa.riskengine.application.port;

import io.github.daiaaaa.riskengine.domain.model.RiskSignal;
import io.github.daiaaaa.riskengine.domain.model.Transaction;

/**
 * Output port: any source of antifraud signals (internal rule, external bureau, ML model).
 * The orchestrator depends only on this interface, never on a concrete source.
 */
public interface SignalProvider {

    String name();

    RiskSignal evaluate(Transaction transaction);
}
