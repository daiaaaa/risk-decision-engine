package io.github.daiaaaa.motorrisco.aplicacao.porta;

import io.github.daiaaaa.motorrisco.aplicacao.RevisaoNaoEncontradaException;
import io.github.daiaaaa.motorrisco.dominio.modelo.DecisaoAnalista;
import io.github.daiaaaa.motorrisco.dominio.modelo.RevisaoPendente;
import io.github.daiaaaa.motorrisco.dominio.modelo.TarefaRevisao;

import java.util.List;

/**
 * Porta de saída: onde ficam as transações que aguardam revisão manual.
 * Duas implementações: em memória (simples, para testes) e Camunda 8 (processo BPMN com
 * tarefa para o analista e prazo).
 */
public interface FilaRevisao {

    /**
     * Abre a revisão se ela ainda não existir. Mensageria entrega "pelo menos uma vez",
     * então a mesma decisão pode chegar repetida.
     *
     * @return verdadeiro se a revisão foi aberta; falso se já existia
     */
    boolean adicionarSeAusente(RevisaoPendente revisao);

    /** Tarefas abertas, da mais antiga para a mais nova. */
    List<TarefaRevisao> pendentes();

    /**
     * Registra a decisão do analista e encerra a tarefa.
     *
     * @throws RevisaoNaoEncontradaException se a tarefa não existe ou já foi concluída
     */
    void concluir(String idTarefa, DecisaoAnalista decisao, String analista, String justificativa);
}
