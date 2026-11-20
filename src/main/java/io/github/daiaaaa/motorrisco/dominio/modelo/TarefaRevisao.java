package io.github.daiaaaa.motorrisco.dominio.modelo;

import java.util.Objects;

/**
 * Uma revisão pendente já transformada em tarefa para um analista.
 *
 * @param idTarefa identificador da tarefa, usado pelo analista para registrar a decisão
 *                 (no Camunda, a chave da user task)
 */
public record TarefaRevisao(String idTarefa, RevisaoPendente revisao) {

    public TarefaRevisao {
        Objects.requireNonNull(idTarefa, "idTarefa");
        Objects.requireNonNull(revisao, "revisao");
    }
}
