package io.github.daiaaaa.motorrisco.aplicacao;

import io.github.daiaaaa.motorrisco.dominio.modelo.AvaliacaoRisco;

/**
 * @param avaliacao a avaliação devolvida ao cliente
 * @param repetida  verdadeiro quando a avaliação veio do registro de idempotência, sem nova avaliação
 */
public record ResultadoAvaliacao(AvaliacaoRisco avaliacao, boolean repetida) {
}
