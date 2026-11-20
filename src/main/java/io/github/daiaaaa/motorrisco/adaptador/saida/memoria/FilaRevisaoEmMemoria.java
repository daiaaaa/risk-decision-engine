package io.github.daiaaaa.motorrisco.adaptador.saida.memoria;

import io.github.daiaaaa.motorrisco.aplicacao.RevisaoNaoEncontradaException;
import io.github.daiaaaa.motorrisco.aplicacao.porta.FilaRevisao;
import io.github.daiaaaa.motorrisco.dominio.modelo.DecisaoAnalista;
import io.github.daiaaaa.motorrisco.dominio.modelo.RevisaoPendente;
import io.github.daiaaaa.motorrisco.dominio.modelo.TarefaRevisao;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fila de revisão em memória. O idTarefa é o próprio idAvaliacao. Some quando a aplicação
 * reinicia e não tem prazo; serve para testes e para rodar sem o Camunda
 * ({@code risco.revisao.motor=memoria}).
 */
public class FilaRevisaoEmMemoria implements FilaRevisao {

    private final Map<String, RevisaoPendente> abertas = new ConcurrentHashMap<>();
    /** Todas as avaliações já recebidas, para que uma revisão concluída não seja reaberta. */
    private final Set<String> recebidas = ConcurrentHashMap.newKeySet();

    @Override
    public boolean adicionarSeAusente(RevisaoPendente revisao) {
        if (!recebidas.add(revisao.idAvaliacao())) {
            return false;
        }
        abertas.put(revisao.idAvaliacao(), revisao);
        return true;
    }

    @Override
    public List<TarefaRevisao> pendentes() {
        return abertas.values().stream()
                .sorted(Comparator.comparing(RevisaoPendente::avaliadoEm))
                .map(r -> new TarefaRevisao(r.idAvaliacao(), r))
                .toList();
    }

    @Override
    public void concluir(String idTarefa, DecisaoAnalista decisao, String analista, String justificativa) {
        if (abertas.remove(idTarefa) == null) {
            throw new RevisaoNaoEncontradaException(idTarefa);
        }
    }
}
