package io.github.daiaaaa.motorrisco.aplicacao;

import io.github.daiaaaa.motorrisco.adaptador.saida.memoria.FilaRevisaoEmMemoria;
import io.github.daiaaaa.motorrisco.dominio.modelo.Decisao;
import io.github.daiaaaa.motorrisco.dominio.modelo.DecisaoAnalista;
import io.github.daiaaaa.motorrisco.dominio.modelo.RevisaoPendente;
import io.github.daiaaaa.motorrisco.dominio.modelo.TarefaRevisao;
import io.github.daiaaaa.motorrisco.dominio.modelo.TipoTransacao;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServicoRevisaoManualTest {

    private final ServicoRevisaoManual servico = new ServicoRevisaoManual(new FilaRevisaoEmMemoria());

    @Test
    void soDecisaoRevisarAbreRevisao() {
        assertThat(servico.receberDecisao(Decisao.APROVAR, revisao("avaliacao-1"))).isFalse();
        assertThat(servico.receberDecisao(Decisao.NEGAR, revisao("avaliacao-2"))).isFalse();
        assertThat(servico.receberDecisao(Decisao.REVISAR, revisao("avaliacao-3"))).isTrue();

        assertThat(servico.pendentes()).extracting(TarefaRevisao::idTarefa).containsExactly("avaliacao-3");
    }

    @Test
    void decidirRemoveATarefaDasPendentes() {
        servico.receberDecisao(Decisao.REVISAR, revisao("avaliacao-1"));

        servico.decidir("avaliacao-1", DecisaoAnalista.NEGAR, "analista-1", "dispositivo suspeito");

        assertThat(servico.pendentes()).isEmpty();
    }

    @Test
    void decidirTarefaInexistenteLancaExcecao() {
        assertThatThrownBy(() -> servico.decidir("nao-existe", DecisaoAnalista.APROVAR, "analista-1", null))
                .isInstanceOf(RevisaoNaoEncontradaException.class)
                .hasMessageContaining("nao-existe");
    }

    @Test
    void decidirDuasVezesNaSegundaLancaExcecao() {
        servico.receberDecisao(Decisao.REVISAR, revisao("avaliacao-1"));
        servico.decidir("avaliacao-1", DecisaoAnalista.APROVAR, "analista-1", null);

        assertThatThrownBy(() -> servico.decidir("avaliacao-1", DecisaoAnalista.NEGAR, "analista-2", null))
                .isInstanceOf(RevisaoNaoEncontradaException.class);
    }

    private static RevisaoPendente revisao(String idAvaliacao) {
        return new RevisaoPendente(idAvaliacao, "tx-1", "cliente-001", new BigDecimal("300.00"),
                TipoTransacao.PIX, 40, false, Instant.parse("2026-11-10T12:00:00Z"));
    }
}
