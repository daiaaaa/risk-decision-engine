package io.github.daiaaaa.motorrisco.adaptador.saida.metricas;

import io.github.daiaaaa.motorrisco.dominio.modelo.AvaliacaoRisco;
import io.github.daiaaaa.motorrisco.dominio.modelo.Decisao;
import io.github.daiaaaa.motorrisco.dominio.modelo.SinalRisco;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MetricasMicrometerTest {

    private final SimpleMeterRegistry registro = new SimpleMeterRegistry();
    private final MetricasMicrometer metricas = new MetricasMicrometer(registro);

    @Test
    void contaDecisoesPorTipoESeForamDegradadas() {
        metricas.decisaoEmitida(avaliacao(Decisao.APROVAR, SinalRisco.pontuado("dispositivo", 5, 50, "ok")));
        metricas.decisaoEmitida(avaliacao(Decisao.APROVAR, SinalRisco.pontuado("dispositivo", 5, 50, "ok")));
        metricas.decisaoEmitida(avaliacao(Decisao.REVISAR, SinalRisco.indisponivel("historico-cliente", "timeout")));

        assertThat(contador(MetricasMicrometer.DECISOES, "decisao", "APROVAR", "degradada", "false")).isEqualTo(2);
        assertThat(contador(MetricasMicrometer.DECISOES, "decisao", "REVISAR", "degradada", "true")).isEqualTo(1);
    }

    @Test
    void separaAvaliacoesNovasRepetidasEConflitos() {
        metricas.decisaoEmitida(avaliacao(Decisao.APROVAR, SinalRisco.pontuado("dispositivo", 5, 50, "ok")));
        metricas.respostaRepetida();
        metricas.respostaRepetida();
        metricas.conflitoIdempotencia();

        assertThat(contador(MetricasMicrometer.IDEMPOTENCIA, "resultado", "nova")).isEqualTo(1);
        assertThat(contador(MetricasMicrometer.IDEMPOTENCIA, "resultado", "repetida")).isEqualTo(2);
        assertThat(contador(MetricasMicrometer.IDEMPOTENCIA, "resultado", "conflito")).isEqualTo(1);
    }

    private double contador(String nome, String... tags) {
        return registro.get(nome).tags(tags).counter().count();
    }

    private static AvaliacaoRisco avaliacao(Decisao decisao, SinalRisco sinal) {
        return new AvaliacaoRisco("avaliacao", "tx", decisao, 5, List.of(sinal),
                Instant.parse("2026-11-10T12:00:00Z"), Duration.ofMillis(80));
    }
}
