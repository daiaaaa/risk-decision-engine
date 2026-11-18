package io.github.daiaaaa.motorrisco.dominio.modelo;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Operação financeira que precisa de uma decisão de risco antes de ser executada.
 */
public record Transacao(
        String idTransacao,
        String idCliente,
        String idDispositivo,
        BigDecimal valor,
        TipoTransacao tipo) {

    public Transacao {
        Objects.requireNonNull(idTransacao, "idTransacao");
        Objects.requireNonNull(idCliente, "idCliente");
        Objects.requireNonNull(idDispositivo, "idDispositivo");
        Objects.requireNonNull(valor, "valor");
        Objects.requireNonNull(tipo, "tipo");
        if (valor.signum() <= 0) {
            throw new IllegalArgumentException("o valor deve ser positivo");
        }
    }
}
