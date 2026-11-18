package io.github.daiaaaa.motorrisco.adaptador.entrada.kafka;

import io.github.daiaaaa.motorrisco.adaptador.saida.kafka.EventoDecisaoRisco;
import io.github.daiaaaa.motorrisco.adaptador.saida.memoria.FilaRevisaoEmMemoria;
import io.github.daiaaaa.motorrisco.aplicacao.ServicoRevisaoManual;
import io.github.daiaaaa.motorrisco.dominio.modelo.Decisao;
import io.github.daiaaaa.motorrisco.dominio.modelo.RevisaoPendente;
import io.github.daiaaaa.motorrisco.dominio.modelo.TipoTransacao;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConsumidorDecisoesTest {

    private final JsonMapper json = JsonMapper.builder().build();
    private final ServicoRevisaoManual servico = new ServicoRevisaoManual(new FilaRevisaoEmMemoria());
    private final ConsumidorDecisoes consumidor = new ConsumidorDecisoes(servico, json);

    @Test
    void decisaoRevisarEntraNaFilaDeRevisao() {
        consumidor.receber(mensagem("avaliacao-1", Decisao.REVISAR, EventoDecisaoRisco.VERSAO_ATUAL));

        assertThat(servico.pendentes())
                .extracting(RevisaoPendente::idAvaliacao)
                .containsExactly("avaliacao-1");
    }

    @Test
    void aprovarENegarNaoPrecisamDeAnalista() {
        consumidor.receber(mensagem("avaliacao-1", Decisao.APROVAR, EventoDecisaoRisco.VERSAO_ATUAL));
        consumidor.receber(mensagem("avaliacao-2", Decisao.NEGAR, EventoDecisaoRisco.VERSAO_ATUAL));

        assertThat(servico.pendentes()).isEmpty();
    }

    @Test
    void mensagemRepetidaGeraUmaUnicaRevisao() {
        String mensagem = mensagem("avaliacao-1", Decisao.REVISAR, EventoDecisaoRisco.VERSAO_ATUAL);

        consumidor.receber(mensagem);
        consumidor.receber(mensagem);

        assertThat(servico.pendentes()).hasSize(1);
    }

    @Test
    void jsonInvalidoEEventoInvalido() {
        assertThatThrownBy(() -> consumidor.receber("isto não é json"))
                .isInstanceOf(EventoInvalidoException.class);
    }

    @Test
    void campoObrigatorioAusenteEEventoInvalido() {
        assertThatThrownBy(() -> consumidor.receber("""
                {"versao":1,"idAvaliacao":"avaliacao-1","decisao":"REVISAR"}
                """))
                .isInstanceOf(EventoInvalidoException.class);
    }

    @Test
    void versaoDesconhecidaEEventoInvalido() {
        assertThatThrownBy(() -> consumidor.receber(mensagem("avaliacao-1", Decisao.REVISAR, 2)))
                .isInstanceOf(EventoInvalidoException.class)
                .hasMessageContaining("versão");
    }

    private String mensagem(String idAvaliacao, Decisao decisao, int versao) {
        return json.writeValueAsString(new EventoDecisaoRisco(versao, idAvaliacao, "tx-1", "cliente-001",
                new BigDecimal("300.00"), TipoTransacao.PIX, decisao, 40, false,
                Instant.parse("2026-11-10T12:00:00Z")));
    }
}
