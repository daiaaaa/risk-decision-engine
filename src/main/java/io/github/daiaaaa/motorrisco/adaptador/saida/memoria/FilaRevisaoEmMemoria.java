package io.github.daiaaaa.motorrisco.adaptador.saida.memoria;

import io.github.daiaaaa.motorrisco.aplicacao.porta.FilaRevisao;
import io.github.daiaaaa.motorrisco.dominio.modelo.RevisaoPendente;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fila de revisão em memória, indexada por idAvaliacao. Some quando a aplicação reinicia;
 * serve até a fase 6, quando a revisão passa a ser um processo no Camunda.
 */
public class FilaRevisaoEmMemoria implements FilaRevisao {

    private final Map<String, RevisaoPendente> revisoes = new ConcurrentHashMap<>();

    @Override
    public boolean adicionarSeAusente(RevisaoPendente revisao) {
        return revisoes.putIfAbsent(revisao.idAvaliacao(), revisao) == null;
    }

    @Override
    public List<RevisaoPendente> pendentes() {
        return revisoes.values().stream()
                .sorted(Comparator.comparing(RevisaoPendente::avaliadoEm))
                .toList();
    }
}
