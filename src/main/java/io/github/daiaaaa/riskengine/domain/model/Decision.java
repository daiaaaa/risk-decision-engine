package io.github.daiaaaa.riskengine.domain.model;

public enum Decision {
    /** Low risk: the transaction can proceed. */
    APPROVE,
    /** Medium risk: a human analyst must review it (Camunda flow in phase 6). */
    REVIEW,
    /** High risk or a blocking rule matched: the transaction is refused. */
    DENY
}
