package io.github.daiaaaa.motorrisco.adaptador.saida.metricas;

import io.github.daiaaaa.motorrisco.aplicacao.porta.ProvedorSinal;
import io.github.daiaaaa.motorrisco.dominio.modelo.SinalRisco;
import io.github.daiaaaa.motorrisco.dominio.modelo.Transacao;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.concurrent.TimeUnit;

/**
 * Decorator que mede quanto cada fonte de sinais leva e se ela respondeu de verdade.
 * Fica por fora do decorator de resiliência, então mede o que o orquestrador realmente espera:
 * uma fonte com timeout aparece com ~200 ms e {@code resultado=degradado}.
 *
 * <p>No Prometheus: {@code risco_fonte_duracao_seconds_bucket{fonte, resultado}}, com histograma
 * para calcular p95 e p99.
 */
public class ProvedorSinalMedido implements ProvedorSinal {

    static final String DURACAO = "risco.fonte.duracao";

    private final ProvedorSinal delegado;
    private final Timer ok;
    private final Timer degradado;

    public ProvedorSinalMedido(ProvedorSinal delegado, MeterRegistry registro) {
        this.delegado = delegado;
        this.ok = timer(registro, delegado.nome(), "ok");
        this.degradado = timer(registro, delegado.nome(), "degradado");
    }

    @Override
    public String nome() {
        return delegado.nome();
    }

    @Override
    public SinalRisco avaliar(Transacao transacao) {
        long inicio = System.nanoTime();
        SinalRisco sinal = delegado.avaliar(transacao);
        (sinal.degradado() ? degradado : ok).record(System.nanoTime() - inicio, TimeUnit.NANOSECONDS);
        return sinal;
    }

    private static Timer timer(MeterRegistry registro, String fonte, String resultado) {
        return Timer.builder(DURACAO)
                .description("Tempo que cada fonte de sinais levou para responder")
                .tag("fonte", fonte)
                .tag("resultado", resultado)
                .publishPercentileHistogram()
                .register(registro);
    }
}
