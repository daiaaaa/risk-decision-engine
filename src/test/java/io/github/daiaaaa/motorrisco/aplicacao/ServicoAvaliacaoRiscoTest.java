package io.github.daiaaaa.motorrisco.aplicacao;

import io.github.daiaaaa.motorrisco.aplicacao.porta.ProvedorSinal;
import io.github.daiaaaa.motorrisco.dominio.modelo.AvaliacaoRisco;
import io.github.daiaaaa.motorrisco.dominio.modelo.Decisao;
import io.github.daiaaaa.motorrisco.dominio.modelo.SinalRisco;
import io.github.daiaaaa.motorrisco.dominio.modelo.TipoTransacao;
import io.github.daiaaaa.motorrisco.dominio.modelo.Transacao;
import io.github.daiaaaa.motorrisco.dominio.politica.CalculadoraRisco;
import io.github.daiaaaa.motorrisco.dominio.politica.PoliticaDecisao;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

class ServicoAvaliacaoRiscoTest {

    private static final Instant AGORA = Instant.parse("2026-11-10T12:00:00Z");

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Clock relogio = Clock.fixed(AGORA, ZoneOffset.UTC);

    private final Transacao transacao = new Transacao(
            "tx-1", "cliente-001", "dispositivo-abc", new BigDecimal("100.00"), TipoTransacao.PIX);

    @AfterEach
    void encerrar() {
        executor.close();
    }

    @Test
    void combinaOsSinaisEmUmaDecisao() {
        ServicoAvaliacaoRisco servico = servicoCom(
                fixo("dispositivo", SinalRisco.pontuado("dispositivo", 70, 50, "dispositivo desconhecido")),
                fixo("historico", SinalRisco.pontuado("historico", 10, 50, "valor habitual")));

        AvaliacaoRisco resultado = servico.avaliar(transacao);

        assertThat(resultado.pontuacao()).isEqualTo(40);
        assertThat(resultado.decisao()).isEqualTo(Decisao.REVISAR);
        assertThat(resultado.sinais()).hasSize(2);
        assertThat(resultado.idTransacao()).isEqualTo("tx-1");
        assertThat(resultado.avaliadoEm()).isEqualTo(AGORA);
    }

    @Test
    void chamaAsFontesEmParalelo() {
        Duration atraso = Duration.ofMillis(300);
        ServicoAvaliacaoRisco servico = servicoCom(
                lento("a", atraso), lento("b", atraso), lento("c", atraso));

        AvaliacaoRisco resultado = servico.avaliar(transacao);

        // Em sequência levaria ~900 ms; em paralelo leva ~300 ms.
        assertThat(resultado.tempoProcessamento()).isLessThan(Duration.ofMillis(700));
    }

    @Test
    void fonteIndisponivelFicaForaDaPontuacaoEImpedeAprovacao() {
        ServicoAvaliacaoRisco servico = servicoCom(
                fixo("dispositivo", SinalRisco.pontuado("dispositivo", 5, 50, "dispositivo conhecido")),
                fixo("historico", SinalRisco.indisponivel("historico", "tempo limite")));

        AvaliacaoRisco resultado = servico.avaliar(transacao);

        // Só o sinal de dispositivo conta: pontuação 5, que sozinha daria APROVAR.
        assertThat(resultado.pontuacao()).isEqualTo(5);
        assertThat(resultado.decisao()).isEqualTo(Decisao.REVISAR);
    }

    private ServicoAvaliacaoRisco servicoCom(ProvedorSinal... provedores) {
        return new ServicoAvaliacaoRisco(List.of(provedores), new CalculadoraRisco(),
                new PoliticaDecisao(40, 75), executor, relogio);
    }

    private static ProvedorSinal fixo(String nome, SinalRisco sinal) {
        return new ProvedorSinal() {
            public String nome() { return nome; }
            public SinalRisco avaliar(Transacao t) { return sinal; }
        };
    }

    private static ProvedorSinal lento(String nome, Duration atraso) {
        return new ProvedorSinal() {
            public String nome() { return nome; }
            public SinalRisco avaliar(Transacao t) {
                try {
                    Thread.sleep(atraso);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return SinalRisco.pontuado(nome, 10, 1, "lento, mas respondeu");
            }
        };
    }
}
