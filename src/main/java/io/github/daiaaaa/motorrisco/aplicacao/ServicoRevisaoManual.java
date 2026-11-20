package io.github.daiaaaa.motorrisco.aplicacao;

import io.github.daiaaaa.motorrisco.aplicacao.porta.FilaRevisao;
import io.github.daiaaaa.motorrisco.dominio.modelo.Decisao;
import io.github.daiaaaa.motorrisco.dominio.modelo.DecisaoAnalista;
import io.github.daiaaaa.motorrisco.dominio.modelo.RevisaoPendente;
import io.github.daiaaaa.motorrisco.dominio.modelo.TarefaRevisao;

import java.util.List;
import java.util.Objects;

/**
 * Revisão manual: abre revisão para as decisões que pedem um analista e registra a decisão dele.
 */
public class ServicoRevisaoManual {

    private final FilaRevisao fila;

    public ServicoRevisaoManual(FilaRevisao fila) {
        this.fila = fila;
    }

    /**
     * @return verdadeiro se a decisão abriu uma nova revisão
     */
    public boolean receberDecisao(Decisao decisao, RevisaoPendente candidata) {
        if (decisao != Decisao.REVISAR) {
            return false;
        }
        return fila.adicionarSeAusente(candidata);
    }

    public List<TarefaRevisao> pendentes() {
        return fila.pendentes();
    }

    /**
     * @param analista quem decidiu; vem do token de quem chamou, nunca do corpo da requisição
     * @throws RevisaoNaoEncontradaException se a tarefa não existe ou já foi concluída
     */
    public void decidir(String idTarefa, DecisaoAnalista decisao, String analista, String justificativa) {
        Objects.requireNonNull(idTarefa, "idTarefa");
        Objects.requireNonNull(decisao, "decisao");
        Objects.requireNonNull(analista, "analista");
        fila.concluir(idTarefa, decisao, analista, justificativa);
    }
}
