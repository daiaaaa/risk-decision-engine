package io.github.daiaaaa.riskengine.domain.model;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * A financial operation that needs a risk decision before it is executed.
 */
public record Transaction(
        String transactionId,
        String customerId,
        String deviceId,
        BigDecimal amount,
        TransactionType type) {

    public Transaction {
        Objects.requireNonNull(transactionId, "transactionId");
        Objects.requireNonNull(customerId, "customerId");
        Objects.requireNonNull(deviceId, "deviceId");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(type, "type");
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
    }
}
