package io.github.daiaaaa.motorrisco.aplicacao.porta;

import io.github.daiaaaa.motorrisco.dominio.modelo.SinalRisco;
import io.github.daiaaaa.motorrisco.dominio.modelo.Transacao;

/**
 * Porta de saída: qualquer fonte de sinais antifraude (regra interna, bureau externo, modelo de ML).
 * O orquestrador depende apenas desta interface, nunca de uma fonte concreta.
 */
public interface ProvedorSinal {

    String nome();

    SinalRisco avaliar(Transacao transacao);
}
