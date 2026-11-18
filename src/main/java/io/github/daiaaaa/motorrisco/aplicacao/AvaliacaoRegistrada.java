package io.github.daiaaaa.motorrisco.aplicacao;

import io.github.daiaaaa.motorrisco.dominio.modelo.AvaliacaoRisco;

import java.util.Objects;

/**
 * Uma avaliação guardada para idempotência, junto com a impressão digital da requisição
 * que a originou.
 */
public record AvaliacaoRegistrada(String impressaoRequisicao, AvaliacaoRisco avaliacao) {

    public AvaliacaoRegistrada {
        Objects.requireNonNull(impressaoRequisicao, "impressaoRequisicao");
        Objects.requireNonNull(avaliacao, "avaliacao");
    }
}
