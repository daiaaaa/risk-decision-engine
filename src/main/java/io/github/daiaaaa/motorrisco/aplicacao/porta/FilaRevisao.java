package io.github.daiaaaa.motorrisco.aplicacao.porta;

import io.github.daiaaaa.motorrisco.dominio.modelo.RevisaoPendente;

import java.util.List;

/**
 * Porta de saída: onde ficam as transações que aguardam revisão manual.
 * Hoje a implementação é em memória; na fase 6 ela passa a iniciar um processo no Camunda.
 */
public interface FilaRevisao {

    /**
     * Adiciona a revisão se ela ainda não estiver na fila. Mensageria entrega "pelo menos uma vez",
     * então a mesma decisão pode chegar repetida.
     *
     * @return verdadeiro se a revisão foi adicionada; falso se já estava na fila
     */
    boolean adicionarSeAusente(RevisaoPendente revisao);

    List<RevisaoPendente> pendentes();
}
