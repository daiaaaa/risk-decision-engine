package io.github.daiaaaa.motorrisco.aplicacao.porta;

import io.github.daiaaaa.motorrisco.dominio.modelo.AvaliacaoRisco;
import io.github.daiaaaa.motorrisco.dominio.modelo.Transacao;

/**
 * Porta de saída: avisa o resto da empresa que uma decisão de risco foi tomada.
 *
 * <p>Contrato: a implementação não pode lançar exceção nem bloquear a chamada. A decisão já
 * foi tomada e precisa ser devolvida ao cliente mesmo que a publicação falhe.
 */
public interface PublicadorDecisoes {

    void publicar(Transacao transacao, AvaliacaoRisco avaliacao);
}
