package io.github.daiaaaa.motorrisco.adaptador.saida.camunda;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Job worker do tipo {@code registrar-decisao-final}, usado pelas três tarefas de serviço do
 * BPMN ("Liberar transação", "Bloquear transação" e "Negar por prazo"). Qual delas rodou se vê
 * pela variável {@code decisaoFinal} (APROVADA, NEGADA ou NEGADA_POR_PRAZO), definida no mapeamento
 * de entrada de cada tarefa.
 *
 * <p>Aqui só há log; um sistema real liberaria ou bloquearia a transação (por exemplo,
 * publicando outro evento no Kafka ou chamando o sistema de pagamentos).
 */
public class TrabalhadorRegistrarDecisaoFinal {

    private static final Logger log = LoggerFactory.getLogger(TrabalhadorRegistrarDecisaoFinal.class);

    @JobWorker(type = "registrar-decisao-final")
    public void registrar(@Variable String idTransacao, @Variable String decisaoFinal,
                          @Variable(name = "analista", optional = true) String analista,
                          @Variable(name = "justificativa", optional = true) String justificativa) {
        if (analista != null) {
            log.info("Transação {}: {} pelo analista {} ({})", idTransacao, decisaoFinal, analista, justificativa);
        } else {
            log.info("Transação {}: {} (prazo de revisão esgotado)", idTransacao, decisaoFinal);
        }
    }
}
