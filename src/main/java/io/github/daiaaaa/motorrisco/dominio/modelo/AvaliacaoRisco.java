package io.github.daiaaaa.motorrisco.dominio.modelo;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Resultado da avaliação de uma transação: a decisão e tudo o que levou a ela.
 */
public record AvaliacaoRisco(
        String idAvaliacao,
        String idTransacao,
        Decisao decisao,
        int pontuacao,
        List<SinalRisco> sinais,
        Instant avaliadoEm,
        Duration tempoProcessamento) {

    public AvaliacaoRisco {
        sinais = List.copyOf(sinais);
    }
}
