package io.github.daiaaaa.motorrisco.adaptador.saida.resiliencia;

import io.github.daiaaaa.motorrisco.aplicacao.porta.ProvedorSinal;
import io.github.daiaaaa.motorrisco.dominio.modelo.SinalRisco;
import io.github.daiaaaa.motorrisco.dominio.modelo.Transacao;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.timelimiter.TimeLimiter;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeoutException;

/**
 * Decorator que protege uma fonte de sinais. Ordem das camadas, de fora para dentro:
 *
 * <pre>
 *   fallback ( circuit breaker ( time limiter ( provedor real ) ) )
 * </pre>
 *
 * <ul>
 *   <li><b>Time limiter</b>: desiste de uma chamada que passa do tempo limite.</li>
 *   <li><b>Circuit breaker</b>: conta falhas e timeouts; quando passam do limite, ele abre e
 *       rejeita as chamadas na hora. Assim a fonte com problema tem tempo para se recuperar
 *       e o orçamento de latência não é gasto esperando por ela.</li>
 *   <li><b>Fallback</b>: qualquer falha vira um sinal degradado em vez de uma exceção, então
 *       uma fonte nunca derruba a avaliação inteira.</li>
 * </ul>
 */
public class ProvedorSinalResiliente implements ProvedorSinal {

    private final ProvedorSinal delegado;
    private final CircuitBreaker circuitBreaker;
    private final TimeLimiter timeLimiter;
    private final ExecutorService executor;

    public ProvedorSinalResiliente(ProvedorSinal delegado,
                                   CircuitBreaker circuitBreaker,
                                   TimeLimiter timeLimiter,
                                   ExecutorService executor) {
        this.delegado = delegado;
        this.circuitBreaker = circuitBreaker;
        this.timeLimiter = timeLimiter;
        this.executor = executor;
    }

    @Override
    public String nome() {
        return delegado.nome();
    }

    @Override
    public SinalRisco avaliar(Transacao transacao) {
        try {
            return circuitBreaker.executeCallable(() ->
                    timeLimiter.executeFutureSupplier(() ->
                            executor.submit(() -> delegado.avaliar(transacao))));
        } catch (CallNotPermittedException e) {
            return SinalRisco.indisponivel(nome(), "circuito aberto: fonte ignorada");
        } catch (TimeoutException e) {
            return SinalRisco.indisponivel(nome(), "tempo limite excedido após "
                    + timeLimiter.getTimeLimiterConfig().getTimeoutDuration().toMillis() + " ms");
        } catch (Exception e) {
            return SinalRisco.indisponivel(nome(), "erro na fonte: " + e.getClass().getSimpleName());
        }
    }

    public CircuitBreaker.State estadoCircuito() {
        return circuitBreaker.getState();
    }
}
