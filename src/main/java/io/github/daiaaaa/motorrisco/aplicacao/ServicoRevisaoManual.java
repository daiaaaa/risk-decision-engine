package io.github.daiaaaa.motorrisco.aplicacao;

import io.github.daiaaaa.motorrisco.aplicacao.porta.FilaRevisao;
import io.github.daiaaaa.motorrisco.dominio.modelo.Decisao;
import io.github.daiaaaa.motorrisco.dominio.modelo.RevisaoPendente;

import java.util.List;

/**
 * Recebe as decisões já tomadas e encaminha para revisão manual somente as que pedem um analista.
 */
public class ServicoRevisaoManual {

    private final FilaRevisao fila;

    public ServicoRevisaoManual(FilaRevisao fila) {
        this.fila = fila;
    }

    /**
     * @return verdadeiro se a decisão gerou uma nova revisão pendente
     */
    public boolean receberDecisao(Decisao decisao, RevisaoPendente candidata) {
        if (decisao != Decisao.REVISAR) {
            return false;
        }
        return fila.adicionarSeAusente(candidata);
    }

    public List<RevisaoPendente> pendentes() {
        return fila.pendentes();
    }
}
