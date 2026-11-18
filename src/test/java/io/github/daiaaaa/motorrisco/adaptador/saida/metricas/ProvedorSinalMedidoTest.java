package io.github.daiaaaa.motorrisco.adaptador.saida.metricas;

import io.github.daiaaaa.motorrisco.aplicacao.porta.ProvedorSinal;
import io.github.daiaaaa.motorrisco.dominio.modelo.SinalRisco;
import io.github.daiaaaa.motorrisco.dominio.modelo.TipoTransacao;
import io.github.daiaaaa.motorrisco.dominio.modelo.Transacao;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class ProvedorSinalMedidoTest {

    private final SimpleMeterRegistry registro = new SimpleMeterRegistry();
    private final Transacao transacao = new Transacao(
            "tx-1", "cliente-001", "dispositivo-abc", new BigDecimal("100.00"), TipoTransacao.PIX);

    @Test
    void registraADuracaoDaFonteQueRespondeu() {
        ProvedorSinalMedido medido = new ProvedorSinalMedido(
                lento(SinalRisco.pontuado("dispositivo", 5, 50, "ok"), 50), registro);

        SinalRisco sinal = medido.avaliar(transacao);

        assertThat(sinal.degradado()).isFalse();
        Timer ok = timer("ok");
        assertThat(ok.count()).isEqualTo(1);
        assertThat(ok.totalTime(TimeUnit.MILLISECONDS)).isGreaterThanOrEqualTo(50);
        assertThat(timer("degradado").count()).isZero();
    }

    @Test
    void separaSinaisDegradados() {
        ProvedorSinalMedido medido = new ProvedorSinalMedido(
                lento(SinalRisco.indisponivel("dispositivo", "timeout"), 0), registro);

        medido.avaliar(transacao);

        assertThat(timer("degradado").count()).isEqualTo(1);
        assertThat(timer("ok").count()).isZero();
    }

    private Timer timer(String resultado) {
        return registro.get(ProvedorSinalMedido.DURACAO)
                .tags("fonte", "dispositivo", "resultado", resultado)
                .timer();
    }

    private static ProvedorSinal lento(SinalRisco sinal, long atrasoMs) {
        return new ProvedorSinal() {
            public String nome() { return "dispositivo"; }
            public SinalRisco avaliar(Transacao t) {
                try {
                    Thread.sleep(atrasoMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return sinal;
            }
        };
    }
}
