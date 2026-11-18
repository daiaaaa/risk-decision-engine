package io.github.daiaaaa.riskengine.adapter.in.web;

import io.github.daiaaaa.riskengine.domain.model.Transaction;
import io.github.daiaaaa.riskengine.domain.model.TransactionType;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Input contract of the API. Validation happens here, at the trust boundary,
 * so the domain only ever receives well-formed data.
 */
public record AssessmentRequest(
        @NotBlank @Size(max = 64) String transactionId,
        @NotBlank @Size(max = 64) String customerId,
        @NotBlank @Size(max = 128) String deviceId,
        @NotNull @Positive @Digits(integer = 13, fraction = 2) BigDecimal amount,
        @NotNull TransactionType type) {

    Transaction toDomain() {
        return new Transaction(transactionId, customerId, deviceId, amount, type);
    }
}
