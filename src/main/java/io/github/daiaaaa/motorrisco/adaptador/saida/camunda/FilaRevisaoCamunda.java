package io.github.daiaaaa.motorrisco.adaptador.saida.camunda;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.search.enums.UserTaskState;
import io.camunda.client.api.search.response.UserTask;
import io.camunda.client.api.search.response.Variable;
import io.github.daiaaaa.motorrisco.aplicacao.RevisaoNaoEncontradaException;
import io.github.daiaaaa.motorrisco.aplicacao.porta.FilaRevisao;
import io.github.daiaaaa.motorrisco.dominio.modelo.DecisaoAnalista;
import io.github.daiaaaa.motorrisco.dominio.modelo.RevisaoPendente;
import io.github.daiaaaa.motorrisco.dominio.modelo.TarefaRevisao;
import io.github.daiaaaa.motorrisco.dominio.modelo.TipoTransacao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * A revisão manual é o processo BPMN {@value #ID_PROCESSO}. Abrir a revisão publica uma
 * mensagem que inicia uma instância; a lista de pendentes consulta as user tasks ativas; e a
 * decisão do analista é registrada completando a tarefa ({@link TrabalhadorRegistrarDecisaoFinal}
 * cuida dos três caminhos de saída).
 */
public class FilaRevisaoCamunda implements FilaRevisao {

    private static final Logger log = LoggerFactory.getLogger(FilaRevisaoCamunda.class);

    static final String ID_PROCESSO = "revisao-manual";
    static final String NOME_MENSAGEM = "decisao-revisar";
    static final String VAR_PRAZO = "prazoRevisao";
    static final String VAR_DECISAO_ANALISTA = "decisaoAnalista";
    static final String VAR_ANALISTA = "analista";
    static final String VAR_JUSTIFICATIVA = "justificativa";

    private final CamundaClient camunda;
    private final Duration prazo;

    public FilaRevisaoCamunda(CamundaClient camunda, Duration prazo) {
        this.camunda = camunda;
        this.prazo = prazo;
    }

    @Override
    public boolean adicionarSeAusente(RevisaoPendente revisao) {
        try {
            camunda.newPublishMessageCommand()
                    .messageName(NOME_MENSAGEM)
                    .withoutCorrelationKey()
                    // Chave de idempotência do Zeebe: a mesma mensagem (mesmo idAvaliacao) não
                    // inicia um segundo processo, mesmo que o Kafka a entregue duas vezes.
                    .messageId(revisao.idAvaliacao())
                    .timeToLive(Duration.ofHours(1))
                    .variables(variaveisDeAbertura(revisao))
                    .send()
                    .join();
            return true;
        } catch (ProblemException e) {
            if (e.details().getStatus() != null && e.details().getStatus() == 409) {
                log.debug("Avaliação {} já tinha processo de revisão aberto", revisao.idAvaliacao());
                return false;
            }
            throw e;
        }
    }

    @Override
    public List<TarefaRevisao> pendentes() {
        return camunda.newUserTaskSearchRequest()
                .filter(f -> f.state(UserTaskState.CREATED).bpmnProcessId(ID_PROCESSO))
                .send()
                .join()
                .items()
                .stream()
                .map(this::paraTarefa)
                .toList();
    }

    @Override
    public void concluir(String idTarefa, DecisaoAnalista decisao, String analista, String justificativa) {
        long chave = parseChave(idTarefa);
        try {
            camunda.newCompleteUserTaskCommand(chave)
                    .variables(Map.of(
                            VAR_DECISAO_ANALISTA, decisao.name(),
                            VAR_ANALISTA, analista,
                            VAR_JUSTIFICATIVA, justificativa == null ? "" : justificativa))
                    .send()
                    .join();
        } catch (ProblemException e) {
            if (e.details().getStatus() != null && e.details().getStatus() == 404) {
                throw new RevisaoNaoEncontradaException(idTarefa);
            }
            throw e;
        }
    }

    private TarefaRevisao paraTarefa(UserTask tarefa) {
        long chave = tarefa.getUserTaskKey();
        Map<String, String> variaveis = camunda.newUserTaskEffectiveVariableSearchRequest(chave)
                .send().join().items().stream()
                .collect(java.util.stream.Collectors.toMap(Variable::getName, Variable::getValue));
        RevisaoPendente revisao = new RevisaoPendente(
                semAspas(variaveis.get("idAvaliacao")),
                semAspas(variaveis.get("idTransacao")),
                semAspas(variaveis.get("idCliente")),
                // O Zeebe guarda a variável como número JSON e perde zeros à direita
                // (300.00 volta como 300.0); a escala é normalizada para 2 casas decimais,
                // igual ao valor monetário original, para o BigDecimal comparar igual.
                new BigDecimal(variaveis.get("valor")).setScale(2, java.math.RoundingMode.UNNECESSARY),
                TipoTransacao.valueOf(semAspas(variaveis.get("tipo"))),
                Integer.parseInt(variaveis.get("pontuacao")),
                Boolean.parseBoolean(variaveis.get("degradada")),
                Instant.parse(semAspas(variaveis.get("avaliadoEm"))));
        return new TarefaRevisao(String.valueOf(chave), revisao);
    }

    /** Variable.getValue() devolve o JSON do valor; em strings, isso inclui as aspas. */
    private static String semAspas(String valorJson) {
        return valorJson.substring(1, valorJson.length() - 1);
    }

    private Map<String, Object> variaveisDeAbertura(RevisaoPendente revisao) {
        return Map.of(
                "idAvaliacao", revisao.idAvaliacao(),
                "idTransacao", revisao.idTransacao(),
                "idCliente", revisao.idCliente(),
                "valor", revisao.valor(),
                "tipo", revisao.tipo().name(),
                "pontuacao", revisao.pontuacao(),
                "degradada", revisao.degradada(),
                "avaliadoEm", revisao.avaliadoEm().toString(),
                VAR_PRAZO, prazo.toString());
    }

    private static long parseChave(String idTarefa) {
        try {
            return Long.parseLong(idTarefa);
        } catch (NumberFormatException e) {
            throw new RevisaoNaoEncontradaException(idTarefa);
        }
    }
}
