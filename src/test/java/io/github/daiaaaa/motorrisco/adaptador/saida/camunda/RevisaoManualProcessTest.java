package io.github.daiaaaa.motorrisco.adaptador.saida.camunda;

import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import io.camunda.process.test.api.assertions.ElementSelectors;
import io.camunda.process.test.api.assertions.ProcessInstanceSelectors;
import io.github.daiaaaa.motorrisco.aplicacao.RevisaoNaoEncontradaException;
import io.github.daiaaaa.motorrisco.aplicacao.ServicoRevisaoManual;
import io.github.daiaaaa.motorrisco.dominio.modelo.Decisao;
import io.github.daiaaaa.motorrisco.dominio.modelo.DecisaoAnalista;
import io.github.daiaaaa.motorrisco.dominio.modelo.RevisaoPendente;
import io.github.daiaaaa.motorrisco.dominio.modelo.TarefaRevisao;
import io.github.daiaaaa.motorrisco.dominio.modelo.TipoTransacao;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static io.camunda.process.test.api.CamundaAssert.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Processo "revisao-manual" de ponta a ponta, contra um Camunda real (Zeebe via Testcontainers,
 * provido pelo CamundaSpringProcessTest). Cobre os três caminhos de saída do BPMN: aprovar,
 * negar e o prazo esgotado, este avançando o relógio do motor em vez de esperar 30 minutos.
 *
 * <p>O {@code CamundaClient} usado pelo {@code FilaRevisaoCamunda} é {@code @Lazy}
 * (ConfiguracaoCamunda): só é resolvido no primeiro uso, depois que o CamundaSpringProcessTest
 * já substituiu o client pelo que aponta para o Camunda do teste (Testcontainers).
 */
@SpringBootTest(properties = {
        "risco.revisao.motor=camunda",
        "risco.revisao.prazo=PT30M",
        "spring.kafka.listener.auto-startup=false",
        "spring.kafka.admin.auto-create=false"
})
@CamundaSpringProcessTest
class RevisaoManualProcessTest {

    @Autowired
    private ServicoRevisaoManual servico;

    @Autowired
    private CamundaProcessTestContext contextoProcesso;

    @Test
    void decisaoRevisarAbreUmaTarefaParaOAnalista() {
        RevisaoPendente revisao = revisao();

        boolean nova = servico.receberDecisao(Decisao.REVISAR, revisao);

        assertThat(nova).isTrue();
        esperar(() -> buscarTarefa(revisao.idAvaliacao()).isPresent());
        TarefaRevisao tarefa = buscarTarefa(revisao.idAvaliacao()).orElseThrow();
        assertThat(tarefa.revisao()).isEqualTo(revisao);
    }

    @Test
    void mensagemRepetidaNaoAbreSegundaTarefa() {
        RevisaoPendente revisao = revisao();

        boolean primeira = servico.receberDecisao(Decisao.REVISAR, revisao);
        boolean segunda = servico.receberDecisao(Decisao.REVISAR, revisao);

        assertThat(primeira).isTrue();
        assertThat(segunda).isFalse();
        esperar(() -> buscarTarefa(revisao.idAvaliacao()).isPresent());
        long quantas = servico.pendentes().stream()
                .filter(t -> t.revisao().idAvaliacao().equals(revisao.idAvaliacao()))
                .count();
        assertThat(quantas).isEqualTo(1);
    }

    @Test
    void analistaAprovaELiberaATransacao() {
        RevisaoPendente revisao = revisao();
        servico.receberDecisao(Decisao.REVISAR, revisao);
        esperar(() -> buscarTarefa(revisao.idAvaliacao()).isPresent());
        String idTarefa = buscarTarefa(revisao.idAvaliacao()).orElseThrow().idTarefa();

        servico.decidir(idTarefa, DecisaoAnalista.APROVAR, "analista-1", "cliente confirmou a compra");

        assertThat(ProcessInstanceSelectors.byProcessId(FilaRevisaoCamunda.ID_PROCESSO))
                .hasCompletedElements(ElementSelectors.byId("LiberarTransacao"))
                .hasCompletedElementsInOrder(ElementSelectors.byId("TransacaoAprovada"));
        esperar(() -> buscarTarefa(revisao.idAvaliacao()).isEmpty());
    }

    @Test
    void analistaNegaEBloqueiaATransacao() {
        RevisaoPendente revisao = revisao();
        servico.receberDecisao(Decisao.REVISAR, revisao);
        esperar(() -> buscarTarefa(revisao.idAvaliacao()).isPresent());
        String idTarefa = buscarTarefa(revisao.idAvaliacao()).orElseThrow().idTarefa();

        servico.decidir(idTarefa, DecisaoAnalista.NEGAR, "analista-1", "dispositivo não reconhecido");

        assertThat(ProcessInstanceSelectors.byProcessId(FilaRevisaoCamunda.ID_PROCESSO))
                .hasCompletedElements(ElementSelectors.byId("BloquearTransacao"))
                .hasCompletedElementsInOrder(ElementSelectors.byId("TransacaoNegada"));
    }

    @Test
    void decidirTarefaJaConcluidaLancaExcecao() {
        RevisaoPendente revisao = revisao();
        servico.receberDecisao(Decisao.REVISAR, revisao);
        esperar(() -> buscarTarefa(revisao.idAvaliacao()).isPresent());
        String idTarefa = buscarTarefa(revisao.idAvaliacao()).orElseThrow().idTarefa();
        servico.decidir(idTarefa, DecisaoAnalista.APROVAR, "analista-1", null);

        assertThatThrownBy(() -> servico.decidir(idTarefa, DecisaoAnalista.NEGAR, "analista-2", null))
                .isInstanceOf(RevisaoNaoEncontradaException.class);
    }

    @Test
    void prazoEsgotadoNegaATransacaoAutomaticamente() {
        RevisaoPendente revisao = revisao();
        servico.receberDecisao(Decisao.REVISAR, revisao);
        esperar(() -> buscarTarefa(revisao.idAvaliacao()).isPresent());

        // Avança o relógio do próprio motor, em vez de esperar 30 minutos de verdade.
        contextoProcesso.increaseTime(Duration.ofMinutes(31));

        assertThat(ProcessInstanceSelectors.byProcessId(FilaRevisaoCamunda.ID_PROCESSO))
                .hasCompletedElements(ElementSelectors.byId("NegarPorPrazo"))
                .hasCompletedElementsInOrder(ElementSelectors.byId("TransacaoNegadaPorPrazo"));
        esperar(() -> buscarTarefa(revisao.idAvaliacao()).isEmpty());
    }

    private Optional<TarefaRevisao> buscarTarefa(String idAvaliacao) {
        return servico.pendentes().stream()
                .filter(t -> t.revisao().idAvaliacao().equals(idAvaliacao))
                .findFirst();
    }

    private static void esperar(BooleanSupplier condicao) {
        long limite = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (!condicao.getAsBoolean()) {
            if (System.nanoTime() > limite) {
                fail("condição não atendida em 30 s");
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static RevisaoPendente revisao() {
        return new RevisaoPendente(UUID.randomUUID().toString(), "tx-" + UUID.randomUUID(), "cliente-001",
                new BigDecimal("300.00"), TipoTransacao.PIX, 40, false, Instant.now());
    }
}
