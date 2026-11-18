package io.github.daiaaaa.motorrisco.adaptador.saida.resiliencia;

import io.github.daiaaaa.motorrisco.aplicacao.porta.ProvedorSinal;
import io.github.daiaaaa.motorrisco.dominio.modelo.SinalRisco;
import io.github.daiaaaa.motorrisco.dominio.modelo.TipoTransacao;
import io.github.daiaaaa.motorrisco.dominio.modelo.Transacao;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ProvedorSinalResilienteTest {

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    private final Transacao transacao = new Transacao(
            "tx-1", "cliente-001", "dispositivo-abc", new BigDecimal("100.00"), TipoTransacao.PIX);

    @AfterEach
    void encerrar() {
        executor.close();
    }

    @Test
    void fonteSaudavelPassaDireto() {
        SinalRisco esperado = SinalRisco.pontuado("dispositivo", 5, 50, "dispositivo conhecido");
        ProvedorSinalResiliente provedor = resiliente(fixo(esperado), Duration.ofSeconds(1), Duration.ofSeconds(60));

        assertThat(provedor.avaliar(transacao)).isEqualTo(esperado);
    }

    @Test
    void fonteLentaEstouraOTempoLimiteEECancelada() throws InterruptedException {
        CountDownLatch interrompida = new CountDownLatch(1);
        ProvedorSinal lenta = provedor(t -> {
            try {
                Thread.sleep(Duration.ofSeconds(5));
            } catch (InterruptedException e) {
                interrompida.countDown();
                Thread.currentThread().interrupt();
            }
            return SinalRisco.pontuado("dispositivo", 5, 50, "tarde demais");
        });
        ProvedorSinalResiliente provedor = resiliente(lenta, Duration.ofMillis(100), Duration.ofSeconds(60));

        long inicio = System.nanoTime();
        SinalRisco sinal = provedor.avaliar(transacao);
        Duration decorrido = Duration.ofNanos(System.nanoTime() - inicio);

        assertThat(sinal.degradado()).isTrue();
        assertThat(sinal.peso()).isZero();
        assertThat(sinal.motivo()).startsWith("tempo limite excedido");
        assertThat(decorrido).isLessThan(Duration.ofMillis(600));
        // A chamada abandonada é interrompida, então não fica ocupando uma thread.
        assertThat(interrompida.await(1, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void fonteComErroViraSinalDegradado() {
        ProvedorSinal quebrada = provedor(t -> {
            throw new IllegalStateException("bureau fora do ar");
        });
        ProvedorSinalResiliente provedor = resiliente(quebrada, Duration.ofSeconds(1), Duration.ofSeconds(60));

        SinalRisco sinal = provedor.avaliar(transacao);

        assertThat(sinal.degradado()).isTrue();
        assertThat(sinal.motivo()).isEqualTo("erro na fonte: IllegalStateException");
    }

    @Test
    void circuitoAbreDepoisDeFalhasRepetidasEParaDeChamarAFonte() {
        AtomicInteger chamadas = new AtomicInteger();
        ProvedorSinal quebrada = provedor(t -> {
            chamadas.incrementAndGet();
            throw new IllegalStateException("bureau fora do ar");
        });
        ProvedorSinalResiliente provedor = resiliente(quebrada, Duration.ofSeconds(1), Duration.ofSeconds(60));

        for (int i = 0; i < 4; i++) {
            provedor.avaliar(transacao);
        }
        assertThat(provedor.estadoCircuito()).isEqualTo(CircuitBreaker.State.OPEN);

        SinalRisco sinal = provedor.avaliar(transacao);

        assertThat(sinal.motivo()).isEqualTo("circuito aberto: fonte ignorada");
        assertThat(chamadas).hasValue(4);
    }

    @Test
    void circuitoFechaDeNovoQuandoAFonteSeRecupera() throws InterruptedException {
        AtomicBoolean saudavel = new AtomicBoolean(false);
        ProvedorSinal instavel = provedor(t -> {
            if (!saudavel.get()) {
                throw new IllegalStateException("bureau fora do ar");
            }
            return SinalRisco.pontuado("dispositivo", 5, 50, "dispositivo conhecido");
        });
        ProvedorSinalResiliente provedor = resiliente(instavel, Duration.ofSeconds(1), Duration.ofMillis(100));

        for (int i = 0; i < 4; i++) {
            provedor.avaliar(transacao);
        }
        assertThat(provedor.estadoCircuito()).isEqualTo(CircuitBreaker.State.OPEN);

        saudavel.set(true);
        Thread.sleep(300); // espera mais que o tempo de circuito aberto -> HALF_OPEN

        assertThat(provedor.estadoCircuito()).isEqualTo(CircuitBreaker.State.HALF_OPEN);
        provedor.avaliar(transacao);
        provedor.avaliar(transacao);
        assertThat(provedor.estadoCircuito()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    private ProvedorSinalResiliente resiliente(ProvedorSinal delegado, Duration tempoLimite, Duration esperaAberto) {
        CircuitBreakerConfig configCircuito = CircuitBreakerConfig.custom()
                .slidingWindowSize(4)
                .minimumNumberOfCalls(4)
                .failureRateThreshold(50)
                .waitDurationInOpenState(esperaAberto)
                .permittedNumberOfCallsInHalfOpenState(2)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .build();
        TimeLimiterConfig configTempo = TimeLimiterConfig.custom()
                .timeoutDuration(tempoLimite)
                .cancelRunningFuture(true)
                .build();
        return new ProvedorSinalResiliente(delegado,
                CircuitBreaker.of("teste", configCircuito), TimeLimiter.of("teste", configTempo), executor);
    }

    private interface Avaliacao {
        SinalRisco aplicar(Transacao transacao);
    }

    private static ProvedorSinal provedor(Avaliacao avaliacao) {
        return new ProvedorSinal() {
            public String nome() { return "dispositivo"; }
            public SinalRisco avaliar(Transacao t) { return avaliacao.aplicar(t); }
        };
    }

    private static ProvedorSinal fixo(SinalRisco sinal) {
        return provedor(t -> sinal);
    }
}
