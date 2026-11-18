package io.github.daiaaaa.motorrisco.dominio.modelo;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Uma transação que recebeu a decisão REVISAR e aguarda um analista.
 *
 * @param degradada verdadeiro quando a revisão foi causada por uma fonte de sinais fora do ar
 */
public record RevisaoPendente(
        String idAvaliacao,
        String idTransacao,
        String idCliente,
        BigDecimal valor,
        TipoTransacao tipo,
        int pontuacao,
        boolean degradada,
        Instant avaliadoEm) {

    public RevisaoPendente {
        Objects.requireNonNull(idAvaliacao, "idAvaliacao");
        Objects.requireNonNull(idTransacao, "idTransacao");
        Objects.requireNonNull(idCliente, "idCliente");
        Objects.requireNonNull(valor, "valor");
        Objects.requireNonNull(tipo, "tipo");
        Objects.requireNonNull(avaliadoEm, "avaliadoEm");
    }
}
