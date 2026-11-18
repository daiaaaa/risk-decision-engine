package io.github.daiaaaa.motorrisco.configuracao;

import io.github.daiaaaa.motorrisco.adaptador.saida.metricas.MetricasMicrometer;
import io.github.daiaaaa.motorrisco.adaptador.saida.metricas.ProvedorSinalMedido;
import io.github.daiaaaa.motorrisco.adaptador.saida.redis.RepositorioAvaliacoesRedis;
import io.github.daiaaaa.motorrisco.adaptador.saida.resiliencia.ProvedorSinalResiliente;
import io.github.daiaaaa.motorrisco.aplicacao.ServicoAvaliacaoIdempotente;
import io.github.daiaaaa.motorrisco.aplicacao.ServicoAvaliacaoRisco;
import io.github.daiaaaa.motorrisco.aplicacao.porta.ProvedorSinal;
import io.github.daiaaaa.motorrisco.aplicacao.porta.PublicadorDecisoes;
import io.github.daiaaaa.motorrisco.aplicacao.porta.RegistroMetricas;
import io.github.daiaaaa.motorrisco.aplicacao.porta.RepositorioAvaliacoes;
import io.github.daiaaaa.motorrisco.dominio.politica.CalculadoraRisco;
import io.github.daiaaaa.motorrisco.dominio.politica.PoliticaDecisao;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Conecta as classes de domínio e de aplicação (que não dependem de framework) ao Spring.
 * É o único lugar que sabe como as peças se encaixam.
 */
@Configuration
@EnableConfigurationProperties(PropriedadesResiliencia.class)
public class ConfiguracaoMotorRisco {

    /**
     * Uma virtual thread por chamada de fonte (Java 21). As chamadas passam a maior parte
     * do tempo esperando I/O, e as virtual threads tornam essa espera barata.
     */
    @Bean(destroyMethod = "close")
    public ExecutorService executorSinais() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean
    public Clock relogio() {
        return Clock.systemUTC();
    }

    @Bean
    public CalculadoraRisco calculadoraRisco() {
        return new CalculadoraRisco();
    }

    @Bean
    public PoliticaDecisao politicaDecisao(
            @Value("${risco.decisao.limiar-revisao}") int limiarRevisao,
            @Value("${risco.decisao.limiar-negacao}") int limiarNegacao) {
        return new PoliticaDecisao(limiarRevisao, limiarNegacao);
    }

    /**
     * Um circuit breaker por fonte, todos com as mesmas configurações. Ficam em um
     * registro para que o estado de cada um possa ser exportado como métrica.
     */
    @Bean
    public CircuitBreakerRegistry registroCircuitBreakers(PropriedadesResiliencia props) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(props.tamanhoJanela())
                .minimumNumberOfCalls(props.minimoChamadas())
                .failureRateThreshold(props.limiteTaxaFalha())
                .waitDurationInOpenState(props.esperaCircuitoAberto())
                .permittedNumberOfCallsInHalfOpenState(props.chamadasSemiAberto())
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .build();
        return CircuitBreakerRegistry.of(config);
    }

    /**
     * Exporta o estado e as chamadas de cada circuit breaker como métricas
     * ({@code resilience4j_circuitbreaker_state} no Prometheus). O Spring Boot liga
     * automaticamente qualquer bean MeterBinder ao registro de métricas.
     */
    @Bean
    public MeterBinder metricasCircuitBreakers(CircuitBreakerRegistry registro) {
        return TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(registro);
    }

    @Bean
    public RegistroMetricas registroMetricas(MeterRegistry meterRegistry) {
        return new MetricasMicrometer(meterRegistry);
    }

    /**
     * Os provedores reais são componentes do Spring. Aqui cada um recebe duas camadas:
     *
     * <pre>
     *   medição ( resiliência ( provedor real ) )
     * </pre>
     *
     * A medição fica por fora para registrar o tempo que o orquestrador realmente espera,
     * incluindo timeouts. O orquestrador só enxerga as versões protegidas e medidas.
     */
    @Bean
    public ServicoAvaliacaoRisco servicoAvaliacaoRisco(List<ProvedorSinal> provedoresReais,
                                                       CircuitBreakerRegistry registro,
                                                       PropriedadesResiliencia props,
                                                       CalculadoraRisco calculadora,
                                                       PoliticaDecisao politica,
                                                       ExecutorService executorSinais,
                                                       Clock relogio,
                                                       MeterRegistry meterRegistry) {
        TimeLimiterConfig configTempoLimite = TimeLimiterConfig.custom()
                .timeoutDuration(props.tempoLimite())
                .cancelRunningFuture(true)
                .build();
        List<ProvedorSinal> provedoresProtegidos = provedoresReais.stream()
                .<ProvedorSinal>map(provedor -> {
                    CircuitBreaker circuitBreaker = registro.circuitBreaker(provedor.nome());
                    TimeLimiter timeLimiter = TimeLimiter.of(provedor.nome(), configTempoLimite);
                    ProvedorSinal resiliente =
                            new ProvedorSinalResiliente(provedor, circuitBreaker, timeLimiter, executorSinais);
                    return new ProvedorSinalMedido(resiliente, meterRegistry);
                })
                .toList();
        return new ServicoAvaliacaoRisco(provedoresProtegidos, calculadora, politica, executorSinais, relogio);
    }

    @Bean
    public RepositorioAvaliacoes repositorioAvaliacoes(StringRedisTemplate redis,
                                                       JsonMapper json,
                                                       @Value("${risco.idempotencia.validade}") Duration validade) {
        return new RepositorioAvaliacoesRedis(redis, json, validade);
    }

    @Bean
    public ServicoAvaliacaoIdempotente servicoAvaliacaoIdempotente(ServicoAvaliacaoRisco avaliador,
                                                                   RepositorioAvaliacoes repositorio,
                                                                   PublicadorDecisoes publicador,
                                                                   RegistroMetricas metricas) {
        return new ServicoAvaliacaoIdempotente(avaliador, repositorio, publicador, metricas);
    }
}
