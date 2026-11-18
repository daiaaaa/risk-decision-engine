package io.github.daiaaaa.motorrisco.adaptador.saida.metricas;

import io.github.daiaaaa.motorrisco.aplicacao.porta.RegistroMetricas;
import io.github.daiaaaa.motorrisco.dominio.modelo.AvaliacaoRisco;
import io.github.daiaaaa.motorrisco.dominio.modelo.SinalRisco;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Métricas de negócio no Micrometer. No Prometheus, os nomes viram
 * {@code risco_decisoes_total} e {@code risco_idempotencia_total}.
 *
 * <p>As tags têm poucos valores possíveis (decisão, sim/não). Nunca usar idTransacao ou idCliente
 * como tag: cada valor novo cria uma série nova no Prometheus, e a memória explode.
 */
public class MetricasMicrometer implements RegistroMetricas {

    static final String DECISOES = "risco.decisoes";
    static final String IDEMPOTENCIA = "risco.idempotencia";

    private final MeterRegistry registro;
    private final Counter repetidas;
    private final Counter conflitos;

    public MetricasMicrometer(MeterRegistry registro) {
        this.registro = registro;
        this.repetidas = contadorIdempotencia("repetida");
        this.conflitos = contadorIdempotencia("conflito");
    }

    @Override
    public void decisaoEmitida(AvaliacaoRisco avaliacao) {
        boolean degradada = avaliacao.sinais().stream().anyMatch(SinalRisco::degradado);
        Counter.builder(DECISOES)
                .description("Decisões de risco emitidas (sem contar reenvios)")
                .tag("decisao", avaliacao.decisao().name())
                .tag("degradada", String.valueOf(degradada))
                .register(registro)
                .increment();
        contadorIdempotencia("nova").increment();
    }

    @Override
    public void respostaRepetida() {
        repetidas.increment();
    }

    @Override
    public void conflitoIdempotencia() {
        conflitos.increment();
    }

    private Counter contadorIdempotencia(String resultado) {
        return Counter.builder(IDEMPOTENCIA)
                .description("Resultado da verificação de idempotência por requisição")
                .tag("resultado", resultado)
                .register(registro);
    }
}
